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
 * Clone code từ Bitbucket với branch cụ thể
 * 
 * @param branch Tên branch cần clone
 * @param workspacePath Thư mục đích (tùy chọn)
 * @return Kết quả clone
 */
def cloneBranch(String branch, String workspacePath = '.') {
    echo "Clone branch: ${branch}"
    
    stage('Tên Stage') {
        def result = cloneFromSSH([
            branch: branch,
            workspacePath: workspacePath
        ])
    }
    
    
    echo "Clone completed: ${branch}"
}

void checkout() {
    echo "Checking out code..."
    // Add your checkout logic here, e.g., git checkout
}

// Export các hàm để sử dụng trong Jenkins pipeline
//return this