
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
        String deployPath = '/app/xangdau'
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

        stage('stop docker compose') {
            dir(deployPath) {
                sh 'docker compose down'
            }
        }

        for (String app in buildList) {
            echo "Building app: ${app}"
            stage("build ${app}") {
                switch(app) {
                    case 'frontend':
                        dir('FrontEnd') {
                            // sh '''
                            //     rm -rf node_modules
                            //     rm -f package-lock.json
                            //     npm i --legacy-peer-deps
                            //     npx ng build
                            // '''
                            sh'''
                                ls -la
                            '''

                            echo "Building frontend... không thể build do máy yếu"
                        }
                        
                    break
                    case 'business_api':
                        dir('Be') {
                            sh'''
                                docker rmi -f xangdau.business.api:latest || true
                                docker build -t xangdau.business.api:latest -f Business_Dockerfile .
                            '''
                        }
                    break
                    case 'auth_api':
                        dir('Be') {
                            sh'''
                                docker rmi -f xangdau.auth.api:latest || true
                                docker build -t xangdau.auth.api:latest -f Auth_Dockerfile .
                            '''
                        }
                    break
                    case 'router_api':
                        dir('Be') {
                            sh'''
                                docker rmi -f xangdau.router.api:latest || true
                                docker build -t xangdau.router.api:latest -f Router_Dockerfile .
                            '''
                        }
                    break
                    case 'puplic_api':
                        dir('Be') {
                            sh'''
                                docker rmi -f xangdau.puplic.api:latest || true
                                docker build -t xangdau.puplic.api:latest -f Public_Dockerfile .
                            '''
                        }
                    break
                }
                
            }
        }

        stage('start docker compose') {
            dir(deployPath) {
                sh 'docker compose up -d'
            }
        }
    }
}


// https://github.com/thanhcongIT/nktc_jenkins_lib.git
// Export các hàm để sử dụng trong Jenkins pipeline
//return this