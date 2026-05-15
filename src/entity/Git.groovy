package entity

/**
 * Git Entity sử dụng Jenkins Pipeline git step
 * 
 * Cung cấp các thao tác để tương tác với Git repository
 * Sử dụng Jenkins pipeline `git` và `checkout` trực tiếp
 * 
 * Cách sử dụng:
 * def git = new entity.Git([
 *     repoUrl: 'https://github.com/user/repo.git',
 *     branch: 'main'
 * ])
 * 
 * git.checkout()
 * git.checkout('feature-branch')
 */
class Git {
    
    private String repoUrl
    private String branch
    private String credentialsId
    private String workspacePath
    private def scriptContext
    private String username
    private String password
    private String remoteName
    private Integer depth
    private Boolean shallow
    private Boolean prune
    private Boolean clean
    private List<String> sparseCheckoutPaths
    private List<String> submodules
    
    /**
     * Constructor
     * @param config Map cấu hình chứa:
     *   - repoUrl: URL của repository (bắt buộc)
     *   - branch: Tên branch (mặc định: main)
     *   - credentialsId: Jenkins credential ID cho git (tùy chọn)
     *   - workspacePath: Đường dẫn thư mục làm việc (tùy chọn)
     *   - script: Pipeline script context (tùy chọn, tự động lấy nếu không truyền)
     *   - username: Username cho HTTPS authentication (tùy chọn)
     *   - password: Password cho HTTPS authentication (tùy chọn)
     *   - remoteName: Tên remote (mặc định: origin)
     *   - depth: Shallow clone depth (tùy chọn)
     *   - shallow: Shallow clone (mặc định: false)
     *   - prune: Prune stale branches (mặc định: false)
     *   - clean: Clean before checkout (mặc định: false)
     *   - sparseCheckoutPaths: Danh sách đường dẫn sparse checkout (tùy chọn)
     *   - submodules: Danh sách submodule paths (tùy chọn)
     */
    Git(Map config) {
        this.repoUrl = config.repoUrl
        this.branch = config.branch ?: 'main'
        this.credentialsId = config.credentialsId
        this.workspacePath = config.workspacePath ?: '.'
        this.scriptContext = config.script
        this.username = config.username
        this.password = config.password
        this.remoteName = config.remoteName ?: 'origin'
        this.depth = config.depth
        this.shallow = config.shallow ?: false
        this.prune = config.prune ?: false
        this.clean = config.clean ?: false
        this.sparseCheckoutPaths = config.sparseCheckoutPaths ?: []
        this.submodules = config.submodules ?: []
    }
    
    /**
     * Lấy script context (ưu tiên config truyền vào, không tự động lấy)
     */
    private def getScript() {
        return scriptContext
    }
    
    /**
     * Tạo URL với credentials
     */
    private String createUrlWithCredentials(String originalUrl, String user, String pass) {
        if (originalUrl.startsWith('git@')) {
            // SSH URL: git@bitbucket.org:owner/repo.git
            def parsed = originalUrl.replace('git@', '').split(':')
            return "https://${user}:${pass}@${parsed[0]}/${parsed[1]}"
        } else if (originalUrl.startsWith('https://')) {
            def urlParts = originalUrl.replace('https://', '').split('/', 2)
            return "https://${user}:${pass}@${urlParts[0]}/${urlParts[1]}"
        }
        return originalUrl
    }
    
    
    // ==================== Thao tác Checkout sử dụng pipeline git ====================
    
    /**
     * Checkout sử dụng Jenkins pipeline git step
     * @return Kết quả checkout
     */
    def checkout() {
        return checkout(branch)
    }
    
    /**
     * Checkout với branch cụ thể sử dụng Jenkins pipeline git step
     * @param targetBranch Tên branch cần checkout
     * @return Kết quả checkout
     */
    def checkout(String targetBranch) {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations. Please provide 'script' in constructor config.")
        }
        
        // Sử dụng trực tiếp Jenkins pipeline git step
        def config = [
            url: repoUrl,
            branch: targetBranch
        ]
        
        if (credentialsId) {
            config.credentialsId = credentialsId
        }
        if (depth != null || shallow) {
            config.depth = depth ?: 1
        }
        if (shallow) {
            config.shallow = true
        }
        
        return script.git(config)
    }
    
    /**
     * Checkout với nhiều tùy chọn nâng cao
     * @param targetBranch Tên branch cần checkout
     * @param options Map tùy chọn bổ sung:
     *   - credentialsId: Credential ID
     *   - depth: Clone depth
     *   - shallow: Shallow clone
     *   - prune: Prune stale branches
     *   - clean: Clean before checkout
     *   - sparseCheckoutPaths: Sparse checkout paths
     *   - submodules: Submodule paths
     * @return Kết quả checkout
     */
    def checkout(String targetBranch, Map options) {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        def config = [
            url: options.repoUrl ?: repoUrl,
            branch: targetBranch
        ]
        
        if (options.credentialsId ?: credentialsId) {
            config.credentialsId = options.credentialsId ?: credentialsId
        }
        if (options.depth ?: depth) {
            config.depth = options.depth ?: depth
        }
        if (options.shallow ?: shallow) {
            config.shallow = true
        }
        if (options.prune ?: prune) {
            config.prune = true
        }
        if (options.clean ?: clean) {
            config.clean = true
        }
        
        return script.git(config)
    }
    
    // ==================== Thao tác lấy code ====================
    
    /**
     * Lấy code về (clone nếu chưa có, pull nếu đã có)
     * Sử dụng git step
     * @return Kết quả thao tác
     */
    def getCode() {
        return getCode(branch)
    }
    
    /**
     * Lấy code với branch cụ thể
     * @param targetBranch Tên branch cần lấy
     * @return Kết quả thao tác
     */
    def getCode(String targetBranch) {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        // Kiểm tra repository đã tồn tại chưa
        def repoFolder = new File(workspacePath)
        def gitFolder = new File(workspacePath, '.git')
        
        if (repoFolder.exists() && gitFolder.exists()) {
            // Đã clone rồi → checkout và pull
            println "Repository đã tồn tại, chuyển sang branch ${targetBranch}..."
            script.sh "cd ${workspacePath} && git checkout ${targetBranch}"
            return pull(targetBranch)
        } else {
            // Chưa clone → checkout (sẽ tự động clone)
            println "Repository chưa tồn tại, checkout branch ${targetBranch}..."
            return checkout(targetBranch)
        }
    }
    
    // ==================== Thao tác Fetch/Pull ====================
    
    /**
     * Fetch từ remote
     * @return Kết quả fetch
     */
    def fetch() {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        return script.sh(script: "cd ${workspacePath} && git fetch --all", returnStdout: true).trim()
    }
    
    /**
     * Pull từ remote
     * @return Kết quả pull
     */
    def pull() {
        return pull(branch)
    }
    
    /**
     * Pull từ branch cụ thể
     * @param remoteBranch Tên remote branch
     * @return Kết quả pull
     */
    def pull(String remoteBranch) {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        return script.sh(script: "cd ${workspacePath} && git pull ${remoteName} ${remoteBranch}", returnStdout: true).trim()
    }
    
    // ==================== Các phương thức bổ sung ====================
    
    /**
     * Lấy thông tin commit hiện tại
     * @return Commit hash
     */
    def getCurrentCommit() {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        return script.sh(script: "cd ${workspacePath} && git rev-parse HEAD", returnStdout: true).trim()
    }
    
    /**
     * Lấy thông tin branch hiện tại
     * @return Tên branch hiện tại
     */
    def getCurrentBranch() {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        return script.sh(script: "cd ${workspacePath} && git rev-parse --abbrev-ref HEAD", returnStdout: true).trim()
    }
    
    /**
     * Lấy danh sách các branch
     * @return Danh sách branch
     */
    def getBranches() {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations.")
        }
        
        def output = script.sh(script: "cd ${workspacePath} && git branch -a", returnStdout: true).trim()
        return output.split('\n').collect { it.trim().replaceAll(/^\* /, '') }
    }
    
    /**
     * Lấy thông tin repository
     * @return Map chứa thông tin repo
     */
    def getRepoInfo() {
        return [
            repoUrl: repoUrl,
            branch: branch,
            credentialsId: credentialsId,
            workspacePath: workspacePath,
            remoteName: remoteName,
            shallow: shallow,
            prune: prune,
            clean: clean
        ]
    }
}