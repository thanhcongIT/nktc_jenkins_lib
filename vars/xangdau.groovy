#!/usr/bin/env groovy

/**
 * XangDau CICD Pipeline
 * 
 * Các hàm tiện ích cho CI/CD process
 */

/**
 * Clone code từ Bitbucket sử dụng SSH
 * 
 * @param config Map cấu hình chứa:
 *   - repoUrl: SSH URL (mặc định: git@bitbucket.org:dvthang2024/xangdau_source.git)
 *   - branch: Tên branch (mặc định: main)
 *   - workspacePath: Thư mục đích
 * @return Kết quả clone
 */
def cloneFromSSH(Map config = [:]) {
    def defaultConfig = [
        repoUrl: 'git@bitbucket.org:dvthang2024/xangdau_source.git',
        branch: 'main',
        workspacePath: '.',
        script: this
    ]
    
    // Merge config với default
    def mergedConfig = defaultConfig + config
    
    // Khởi tạo Git entity
    def git = new entity.Git(mergedConfig)
    
    // Thực hiện getCode (clone nếu chưa có, pull nếu đã có)
    return git.getCode(mergedConfig.branch)
}

/**
 * Clone code từ Bitbucket sử dụng HTTPS với username/password
 * 
 * @param config Map cấu hình chứa:
 *   - repoUrl: HTTPS URL (mặc định: https://bitbucket.org/dvthang2024/xangdau_source.git)
 *   - branch: Tên branch (mặc định: main)
 *   - workspacePath: Thư mục đích
 *   - username: Username Bitbucket
 *   - password: Password Bitbucket
 * @return Kết quả clone
 */
def cloneFromHTTPS(Map config = [:]) {
    def defaultConfig = [
        repoUrl: 'https://thanhcongIT@bitbucket.org/dvthang2024/xangdau_source.git',
        branch: 'main',
        workspacePath: '.',
        script: this
    ]
    
    // Merge config với default
    def mergedConfig = defaultConfig + config
    
    // Khởi tạo Git entity với username/password
    def git = new entity.Git(mergedConfig)
    
    // Thực hiện getCode (clone nếu chưa có, pull nếu đã có)
    return git.getCode(mergedConfig.branch)
}

/**
 * Clone code từ Bitbucket với branch cụ thể
 * Sử dụng Jenkins credentials có tên "sourceAccount"
 * 
 * @param branch Tên branch cần clone
 * @param workspacePath Thư mục đích (tùy chọn)
 * @return Kết quả clone
 */
def cloneBranch(String branch, String workspacePath = '.') {
    node {
        echo "Clone branch: ${branch} with credentials"
        
        def result = withCredentials([
            usernamePassword(
                credentialsId: 'sourceAccount',
                usernameVariable: 'GIT_USERNAME',
                passwordVariable: 'GIT_PASSWORD'
            )
        ]) {
            // Chuyển SSH URL sang HTTPS URL
            def sshUrl = 'git@bitbucket.org:dvthang2024/xangdau_source.git'
            def httpsUrl = sshUrl.replace('git@', '').replace(':', '/')
            def authUrl = "https://${env.GIT_USERNAME}:${env.GIT_PASSWORD}@${httpsUrl}"
            
            echo "Cloning from: https://${env.GIT_USERNAME}@bitbucket.org/..."
            
            sh(script: "git clone -b ${branch} ${authUrl} ${workspacePath}", returnStdout: true).trim()
        }
        
        echo "Clone completed: ${branch}"
        return result
    }
}

void checkout() {
    echo "Checking out code..."
    // Add your checkout logic here, e.g., git checkout
}

private boolean isSshAgentRunning() {
    try {
        if (!env.SSH_AGENT_PID) {
            return false
        }

        return sh(script: 'ps -p "$SSH_AGENT_PID" >/dev/null 2>&1', returnStatus: true) == 0
    } catch (ignored) {
        return false
    }
}

private void ensureSshAgent() {
    if (isSshAgentRunning()) {
        echo "ssh-agent is already running"
        return
    }

    echo "ssh-agent is not running, starting it now"
    def agentOutput = sh(script: 'ssh-agent -s', returnStdout: true).trim()

    def authSockMatch = (agentOutput =~ /SSH_AUTH_SOCK=([^;]+);/)
    def agentPidMatch = (agentOutput =~ /SSH_AGENT_PID=([0-9]+);/)

    if (authSockMatch.find()) {
        env.SSH_AUTH_SOCK = authSockMatch.group(1)
    }
    if (agentPidMatch.find()) {
        env.SSH_AGENT_PID = agentPidMatch.group(1)
    }

    echo "ssh-agent started"
}

private boolean hasBitbucketWorkKeyLoaded() {
    def keyPath = "~/.ssh/bitbucket_work"

    if (sh(script: "test -f \"${keyPath}\"", returnStatus: true) != 0) {
        throw new Exception("SSH private key not found: ${keyPath}")
    }

    def keyFingerprint = sh(
        script: "ssh-keygen -lf \"${keyPath}\" -E sha256 | awk '{print \$2}'",
        returnStdout: true
    ).trim()

    def loadedFingerprints = sh(
        script: 'ssh-add -l -E sha256 2>/dev/null | awk "{print $2}"',
        returnStdout: true
    ).trim()

    return loadedFingerprints.split(/\r?\n/).any { it == keyFingerprint }
}

private void ensureBitbucketWorkKeyLoaded() {
    if (hasBitbucketWorkKeyLoaded()) {
        echo "SSH key ~/.ssh/bitbucket_work is already loaded in ssh-agent"
        return
    }

    def keyPath = "~/.ssh/bitbucket_work"
    echo "SSH key ~/.ssh/bitbucket_work is not loaded, adding it now"

    if (sh(script: "ssh-add \"${keyPath}\"", returnStatus: true) != 0) {
        throw new Exception("Failed to add SSH key to ssh-agent: ${keyPath}")
    }

    echo "SSH key ~/.ssh/bitbucket_work loaded successfully"
}

private void ensureBitbucketWorkKeyLoaded(String passphraseCredentialId) {
    if (!passphraseCredentialId) {
        ensureBitbucketWorkKeyLoaded()
        return
    }

    if (hasBitbucketWorkKeyLoaded()) {
        echo "SSH key ~/.ssh/bitbucket_work is already loaded in ssh-agent"
        return
    }

    def keyPath = "~/.ssh/bitbucket_work"
    echo "SSH key ~/.ssh/bitbucket_work is not loaded, adding it with Jenkins Credentials"

    withCredentials([string(credentialsId: passphraseCredentialId, variable: 'SSH_KEY_PASSPHRASE')]) {
        def askpassScript = "${pwd(tmp: true)}/ssh-askpass.sh"
        writeFile(
            file: askpassScript,
            text: '''#!/bin/sh
echo "$SSH_KEY_PASSPHRASE"
'''
        )
        sh(script: "chmod 700 \"${askpassScript}\"")

        def addStatus = sh(
            script: "DISPLAY=none SSH_ASKPASS=\"${askpassScript}\" SSH_ASKPASS_REQUIRE=force setsid -w ssh-add \"${keyPath}\"",
            returnStatus: true
        )

        if (addStatus != 0) {
            throw new Exception("Failed to add SSH key to ssh-agent using credential: ${passphraseCredentialId}")
        }
    }

    echo "SSH key ~/.ssh/bitbucket_work loaded successfully"
}

def getCode(Map config = [:]) {
    echo "Getting code..."
    node {
        dir('~') {
            ensureSshAgent()
            ensureBitbucketWorkKeyLoaded('SSH_KEY')
        }
        

        def git = new entity.Git([
            repoUrl: 'git@bitbucket.org:dvthang2024/xangdau_source.git',
            branch: 'main',
            workspacePath: '.',
            script: this
        ])

        return git.getCode('main')
    }
}


// https://github.com/thanhcongIT/nktc_jenkins_lib.git
// Export các hàm để sử dụng trong Jenkins pipeline
//return this