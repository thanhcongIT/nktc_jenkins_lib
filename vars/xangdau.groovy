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
        repoUrl: 'https://bitbucket.org/dvthang2024/xangdau_source.git',
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

// Export các hàm để sử dụng trong Jenkins pipeline
//return this