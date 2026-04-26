package entity

import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.UserRemoteConfig
import hudson.plugins.git.extensions.GitExtension
import hudson.plugins.git.extensions.impl.CleanBeforeCheckout
import hudson.plugins.git.extensions.impl.DepthOption
import hudson.plugins.git.extensions.impl.PruneStaleBranch
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths
import hudson.plugins.git.extensions.impl.SubmoduleOption
import hudson.util.DescribableList
import org.jenkinsci.plugins.pipeline.utility.steps.scm.GitStep

/**
 * Git Entity sử dụng Jenkins Git Plugin (GitSCM)
 * 
 * Cung cấp các thao tác để tương tác với Git repository
 * Sử dụng GitSCM class từ Jenkins Git Plugin
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
     * Tạo UserRemoteConfig từ repoUrl và credentials
     */
    private UserRemoteConfig createUserRemoteConfig() {
        def url = repoUrl
        def credId = credentialsId
        
        // Nếu có username/password, sử dụng để tạo URL với credentials
        if (username && password) {
            url = createUrlWithCredentials(repoUrl, username, password)
            credId = null // Không cần credentialsId khi đã có URL với credentials
        }
        
        return new UserRemoteConfig(url, remoteName, null, credId)
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
    
    /**
     * Tạo BranchSpec cho branch
     */
    private BranchSpec createBranchSpec(String branchName) {
        return new BranchSpec(branchName)
    }
    
    /**
     * Tạo GitSCM instance
     */
    private GitSCM createGitSCM(String targetBranch) {
        def remoteConfigs = [createUserRemoteConfig()] as List<UserRemoteConfig>
        def branchSpec = createBranchSpec(targetBranch ?: branch)
        
        def extensions = new DescribableList<GitExtension, GitExtension>()
        
        // Thêm các extensions
        if (clean) {
            extensions.add(new CleanBeforeCheckout())
        }
        if (prune) {
            extensions.add(new PruneStaleBranch())
        }
        if (depth != null || shallow) {
            def depthVal = depth ?: 1
            extensions.add(new DepthOption(depthVal, shallow))
        }
        if (sparseCheckoutPaths?.size() > 0) {
            extensions.add(new SparseCheckoutPaths(sparseCheckoutPaths))
        }
        if (submodules?.size() > 0) {
            extensions.add(new SubmoduleOption(
                false, // disableSubmodules
                null,  // recursiveSubmodules
                null,  // trackingSubmodules
                null,  // reference
                null,  // parentCredentials
                submodules as String[]
            ))
        }
        
        return new GitSCM(
            remoteConfigs,
            branchSpec,
            null, // buildChooser
            extensions,
            null, // gitTool
            null  // cloneOptions
        )
    }
    
    // ==================== Thao tác Checkout sử dụng GitSCM ====================
    
    /**
     * Checkout sử dụng GitStep (Jenkins Pipeline Utility)
     * @return Kết quả checkout
     */
    def checkout() {
        return checkout(branch)
    }
    
    /**
     * Checkout với branch cụ thể sử dụng GitStep
     * @param targetBranch Tên branch cần checkout
     * @return Kết quả checkout
     */
    def checkout(String targetBranch) {
        def script = getScript()
        
        if (script == null) {
            throw new Exception("Script context is required for GitSCM operations. Please provide 'script' in constructor config.")
        }
        
        // Sử dụng GitStep từ Jenkins Pipeline Utility
        def gitStep = new GitStep([
            url: repoUrl,
            branch: targetBranch,
            credentialsId: credentialsId,
            changelog: false,
            poll: false
        ])
        
        // Thêm các options
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
        
        return script.git config
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
        
        return script.git config
    }
    
    // ==================== Thao tác lấy code ====================
    
    /**
     * Lấy code về (clone nếu chưa có, pull nếu đã có)
     * Sử dụng GitSCM thông qua git step
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
     * Fetch từ remote sử dụng GitSCM
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
     * Tạo GitSCM object trực tiếp (cho advanced usage)
     * @param targetBranch Tên branch
     * @return GitSCM instance
     */
    GitSCM createSCM(String targetBranch) {
        return createGitSCM(targetBranch)
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