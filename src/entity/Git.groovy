package entity

/**
 * Git Entity
 * 
 * Cung cấp các thao tác để tương tác với Git repository
 * Sử dụng lệnh git thông qua shell
 * 
 * Cách sử dụng:
 * def git = new entity.Git([
 *     repoUrl: 'https://github.com/user/repo.git',
 *     branch: 'main'
 * ])
 * 
 * git.fetch()
 * git.pull()
 */
class Git {
    
    private String repoUrl
    private String branch
    private String credentialsId
    private String workspacePath
    private def scriptContext
    private String username
    private String password
    
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
     */
    Git(Map config) {
        this.repoUrl = config.repoUrl
        this.branch = config.branch ?: 'main'
        this.credentialsId = config.credentialsId
        this.workspacePath = config.workspacePath ?: '.'
        this.scriptContext = config.script
        this.username = config.username
        this.password = config.password
    }
    
    /**
     * Clone với Jenkins credentials
     * @param targetBranch Tên branch cần clone
     * @param targetPath Thư mục đích (tùy chọn)
     * @return Kết quả clone
     */
    def cloneWithCredentials(String targetBranch, String targetPath = null) {
        def path = targetPath ?: workspacePath
        def cloneUrl = getHttpsUrl()
        
        // Sử dụng withCredentials để inject credentials
        return scriptContext.withCredentials([
            scriptContext.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'GIT_USERNAME',
                passwordVariable: 'GIT_PASSWORD'
            )
        ]) {
            def authUrl = cloneUrl.replace('https://', "https://\${env.GIT_USERNAME}:\${env.GIT_PASSWORD}@")
            return scriptContext.sh(script: "git clone -b ${targetBranch} ${authUrl} ${path}", returnStdout: true).trim()
        }
    }
    
    /**
     * Lấy HTTPS URL từ SSH URL
     */
    private String getHttpsUrl() {
        if (repoUrl.startsWith('git@')) {
            // git@bitbucket.org:dvthang2024/xangdau_source.git
            // → https://bitbucket.org/dvthang2024/xangdau_source.git
            def parsed = repoUrl.replace('git@', '').split(':')
            return "https://${parsed[0]}/${parsed[1]}"
        }
        return repoUrl
    }
    
    /**
     * Lấy URL với credentials cho HTTPS
     */
    private String getUrlWithCredentials() {
        if (username && password) {
            // Chuyển SSH URL sang HTTPS URL nếu cần
            def httpsUrl = repoUrl
            if (repoUrl.startsWith('git@')) {
                // git@bitbucket.org:dvthang2024/xangdau_source.git
                // → https://dvthang2024@bitbucket.org/dvthang2024/xangdau_source.git
                def parsed = repoUrl.replace('git@', '').split(':')
                httpsUrl = "https://${username}:${password}@${parsed[0]}/${parsed[1]}"
            } else if (repoUrl.startsWith('https://')) {
                // Thêm credentials vào URL
                def urlParts = repoUrl.replace('https://', '').split('/', 2)
                httpsUrl = "https://${username}:${password}@${urlParts[0]}/${urlParts[1]}"
            }
            return httpsUrl
        }
        return repoUrl
    }
    
    /**
     * Helper method để gọi sh với pipeline context
     */
    private def sh(Map args) {
        if (scriptContext != null) {
            return scriptContext.sh(args)
        } else {
            // Thử lấy context từ binding
            return binding.variables['sh']?.call(args) ?: new groovy.lang.GroovyShell(binding).evaluate("sh(args)")
        }
    }
    
    // ==================== Thao tác Clone ====================
    
    /**
     * Clone repository về thư mục làm việc
     * @param targetPath Thư mục đích (tùy chọn, mặc định là workspacePath)
     * @return Kết quả clone
     */
    def clone(String targetPath = null) {
        def path = targetPath ?: workspacePath
        def cloneUrl = getUrlWithCredentials()
        return sh(script: "git clone ${cloneUrl} ${path}", returnStdout: true).trim()
    }
    
    /**
     * Clone repository với branch cụ thể
     * @param targetBranch Tên branch cần clone
     * @param targetPath Thư mục đích (tùy chọn)
     * @return Kết quả clone
     */
    def cloneBranch(String targetBranch, String targetPath = null) {
        def path = targetPath ?: workspacePath
        //def cloneUrl = getUrlWithCredentials()
        def cloneUrl = "https://thanhcongIT:thanhcong%4012344321@bitbucket.org/dvthang2024/xangdau_source.git"
        return sh(script: "git clone -b ${targetBranch} ${cloneUrl} ${path}", returnStdout: true).trim()
    }
    
    // ==================== Thao tác lấy code ====================
    
    /**
     * Lấy code về (clone nếu chưa có, pull nếu đã có)
     * @return Kết quả thao tác
     */
    def getCode() {
        def repoFolder = new File(workspacePath)
        
        if (repoFolder.exists() && new File(workspacePath, '.git').exists()) {
            // Đã clone rồi → pull code mới nhất
            println "Repository đã tồn tại, thực hiện pull..."
            return pull()
        } else {
            // Chưa clone → clone về
            println "Repository chưa tồn tại, thực hiện clone..."
            return clone()
        }
    }
    
    /**
     * Lấy code với branch cụ thể
     * @param targetBranch Tên branch cần lấy
     * @return Kết quả thao tác
     */
    def getCode(String targetBranch) {
        def repoFolder = new File(workspacePath)
        
        if (repoFolder.exists() && new File(workspacePath, '.git').exists()) {
            // Đã clone rồi → checkout và pull
            println "Repository đã tồn tại, chuyển sang branch ${targetBranch} và pull..."
            sh(script: "cd ${workspacePath} && git checkout ${targetBranch}")
            return pullFrom(targetBranch)
        } else {
            // Chưa clone → clone với branch cụ thể
            println "Repository chưa tồn tại, clone branch ${targetBranch}..."
            return cloneBranch(targetBranch)
        }
    }
    
    // ==================== Thao tác Fetch/Pull ====================
    
    /**
     * Fetch từ remote
     * @return Kết quả fetch
     */
    def fetch() {
        return sh(script: "cd ${workspacePath} && git fetch --all", returnStdout: true).trim()
    }
    
    /**
     * Pull từ remote
     * @return Kết quả pull
     */
    def pull() {
        return sh(script: "cd ${workspacePath} && git pull origin ${branch}", returnStdout: true).trim()
    }
    
    /**
     * Pull từ branch cụ thể
     * @param remoteBranch Tên remote branch
     * @return Kết quả pull
     */
    def pullFrom(String remoteBranch) {
        return sh(script: "cd ${workspacePath} && git pull origin ${remoteBranch}", returnStdout: true).trim()
    }
}