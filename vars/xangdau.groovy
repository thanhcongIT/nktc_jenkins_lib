
def getCode(Map config = [:]) {
    echo "Getting code..."
    node {
        // sh(script: "source ~/.ssh/agent.env || true", returnStdout: true).trim()
        // sh(script: "source ~/.ssh/agent.env")
        // sh(script: ". ~/.ssh/agent.env
        //         git ls-remote git@bitbucket.org:dvthang2024/xangdau_source.git
        // ")

        // sh '''
        //     . /var/lib/jenkins/agent.env
        //     ssh-add -l
        //     git ls-remote git@bitbucket.org:dvthang2024/xangdau_source.git
        // '''

        // sshagent(['git-ssh-key']) {
        //     sh 'git ls-remote git@bitbucket.org:dvthang2024/xangdau_source.git'
        // }

        checkout([
            $class: 'GitSCM',
            branches: [[name: 'main']],
            userRemoteConfigs: [[
                url: 'git@bitbucket.org:dvthang2024/xangdau_source.git',
                credentialsId: 'git-ssh-key'
            ]]
        ])
        
    }
}

def buildApp() {
    node {
        ArrayList buildList = env.build_list.split(',').collect { it.trim() }
        echo "Building app..."
        echo "branch ${env.branch}"
        echo "build list ${buildList}"
        
        stage('get code') {
            checkout([
                $class: 'GitSCM',
                branches: [[name: 'main']],
                userRemoteConfigs: [[
                    url: 'git@bitbucket.org:dvthang2024/xangdau_source.git',
                    credentialsId: 'git-ssh-key'
                ]]
            ])
        }

        for (String app in buildList) {
            echo "Building app: ${app}"
        }
    }
}


// https://github.com/thanhcongIT/nktc_jenkins_lib.git
// Export các hàm để sử dụng trong Jenkins pipeline
//return this