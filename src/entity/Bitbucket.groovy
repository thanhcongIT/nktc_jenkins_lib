package entity

/**
 * Bitbucket Entity
 * 
 * Cung cấp các thao tác để tương tác với Bitbucket Cloud REST API v2
 * Xác thực: Basic Auth với username và App Password
 * 
 * Cách sử dụng:
 * def bitbucket = new entity.Bitbucket([
 *     workspace: 'your-workspace',
 *     repo: 'your-repo',
 *     username: 'your-username',
 *     appPassword: 'your-app-password'
 * ])
 * 
 * def latestCommit = bitbucket.getLatestCommit('main')
 */
class Bitbucket {
    
    private String workspace
    private String repo
    private String username
    private String appPassword
    private String baseUrl
    
    /**
     * Constructor
     * @param config Map cấu hình chứa:
     *   - workspace: Bitbucket workspace (bắt buộc)
     *   - repo: Repository slug (bắt buộc)
     *   - username: Bitbucket username (bắt buộc)
     *   - appPassword: Bitbucket App Password (bắt buộc)
     *   - baseUrl: Tùy chọn custom base URL (mặc định là api.bitbucket.org)
     */
    Bitbucket(Map config) {
        this.workspace = config.workspace
        this.repo = config.repo
        this.username = config.username
        this.appPassword = config.appPassword
        this.baseUrl = config.baseUrl ?: 'https://api.bitbucket.org/2.0'
    }
    
    // ==================== Các phương thức hỗ trợ ====================
    
    /**
     * Thực hiện HTTP request đến Bitbucket API
     */
    private def makeRequest(String endpoint, String method = 'GET', Map body = null) {
        def url = "${baseUrl}${endpoint}"
        def conn = new URL(url).openConnection()
        
        conn.setRequestMethod(method)
        conn.setRequestProperty('Authorization', 'Basic ' + "${username}:${appPassword}".bytes.encodeBase64().toString())
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.setRequestProperty('Accept', 'application/json')
        
        if (body) {
            conn.setDoOutput(true)
            def jsonBody = new groovy.json.JsonBuilder(body).toString()
            conn.getOutputStream().write(jsonBody.getBytes('UTF-8'))
        }
        
        def responseCode = conn.getResponseCode()
        def response = conn.getInputStream().text
        
        if (responseCode >= 400) {
            throw new Exception("Bitbucket API error: ${responseCode} - ${response}")
        }
        
        return new groovy.json.JsonSlurper().parseText(response)
    }
    
    // ==================== Thao tác lấy code ====================
    
    /**
     * Lấy commit mới nhất của một branch
     * @param branch Tên branch (mặc định: main)
     * @return Commit mới nhất
     */
    def getLatestCommit(String branch = 'main') {
        def result = makeRequest("/repositories/${workspace}/${repo}/commits/${branch}")
        return result.values ? result.values[0] : null
    }
    
    /**
     * Lấy danh sách các commit gần đây
     * @param branch Tên branch
     * @param pagelen Số lượng commit cần lấy (mặc định: 10)
     * @return Danh sách commit
     */
    def getCommits(String branch = 'main', int pagelen = 10) {
        return makeRequest("/repositories/${workspace}/${repo}/commits?branch=${branch}&pagelen=${pagelen}")
    }
    
    /**
     * Lấy thông tin chi tiết của một commit
     * @param commitHash Hash hoặc SHA của commit
     * @return Chi tiết commit
     */
    def getCommitDetail(String commitHash) {
        return makeRequest("/repositories/${workspace}/${repo}/commit/${commitHash}")
    }
    
    /**
     * Lấy danh sách các branch
     * @return Danh sách branch
     */
    def getBranches() {
        return makeRequest("/repositories/${workspace}/${repo}/refs/branches")
    }
    
    /**
     * Lấy danh sách các tag
     * @return Danh sách tag
     */
    def getTags() {
        return makeRequest("/repositories/${workspace}/${repo}/refs/tags")
    }
}