# PayProp OAuth2 Client Secret Mismatch Investigation

**Date**: September 10, 2025  
**Issue**: `{"error":"invalid_grant","error_description":"client_secret mismatch"}`  
**Client ID**: `Propsk`  
**Environment**: Production vs Staging comparison

---

## **Summary**

PayProp OAuth2 token exchange consistently fails with "client_secret mismatch" in production, despite using fresh credentials from PayProp. All application code appears correct, suggesting a PayProp-side configuration issue.

---

## **PayProp API Documentation Reference**

### **OAuth v2.0 Flow (Auth Code Grant)**
From: https://uk.payprop.com/api/docs/agency#section/2.-Authentication-and-Security

**Step 1 - Authorization URL:**
```
https://uk.payprop.com/api/oauth/authorize?response_type=code&client_id=YourAppClientID \
&redirect_uri=https%3A%2F%2Fyourapp.com%2Fcallback&scope=read:export:beneficiaries%20read:export:tenants
```

**Step 2 - Token Exchange:**
```
POST /api/oauth/access_token HTTP/1.1
Host: uk.payprop.com
Content-Type: application/x-www-form-urlencoded

code=43423d76123a1124f2&client_id=YourAppClientID&client_secret=YourAppClientSecret& \
redirect_uri=https%3A%2F%2Fyourapp.com%2Fcallback&grant_type=authorization_code
```

**Step 3 - Refresh Token:**
```
POST /api/oauth/access_token HTTP/1.1
Host: uk.payprop.com
Content-Type: application/x-www-form-urlencoded

client_id=YourAppClientID&refresh_token=MTQyNjUxOTM0MC01OTA3&grant_type=refresh_token
```

**Note**: Refresh token requests do **NOT** include `client_secret` parameter.

---

## **Current Implementation**

### **Environment Variables**

**Production:**
```
PAYPROP_CLIENT_ID=Propsk
PAYPROP_CLIENT_SECRET=rphobpAUw1jgXI52
PAYPROP_AUTH_URL=https://uk.payprop.com/api/oauth/authorize
PAYPROP_TOKEN_URL=https://uk.payprop.com/api/oauth/access_token
PAYPROP_REDIRECT_URI=https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback
PAYPROP_SCOPES=
```

**Staging:**
```
PAYPROP_CLIENT_ID=Propsk
PAYPROP_CLIENT_SECRET=L7GJfqHWduV9IdU7
PAYPROP_AUTH_URL=https://ukapi.staging.payprop.com/oauth/authorize
PAYPROP_TOKEN_URL=https://ukapi.staging.payprop.com/oauth/access_tok
PAYPROP_REDIRECT_URI=https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback
PAYPROP_SCOPES="read:export:beneficiaries read:export:tenants read:export:properties"
```

### **OAuth Service Configuration**

```java
@Service
public class PayPropOAuth2Service {
    
    @Value("${payprop.oauth2.client-id}")
    private String clientId;
    
    @Value("${payprop.oauth2.client-secret}")
    private String clientSecret;
    
    @Value("${payprop.oauth2.authorization-url}")
    private String authorizationUrl;
    
    @Value("${payprop.oauth2.token-url}")
    private String tokenUrl;
    
    @Value("${payprop.oauth2.redirect-uri}")
    private String redirectUri;
    
    @Value("${payprop.oauth2.scopes}")
    private String scopes;
}
```

### **Authorization URL Building**

```java
public String getAuthorizationUrl(String state) {
    System.out.println("🔧 BUILDING AUTHORIZATION URL:");
    System.out.println("   Base URL: " + authorizationUrl);
    System.out.println("   Client ID: " + clientId);
    System.out.println("   Redirect URI: " + redirectUri);
    System.out.println("   Scopes (raw): " + scopes);
    System.out.println("   State: " + state);
    
    // Clean scopes to remove line breaks and normalize spaces
    String cleanedScopes = scopes != null ? 
        scopes.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim() : "";
    
    System.out.println("   Scopes (cleaned): " + cleanedScopes);
    
    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(authorizationUrl)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state);
    
    // Only add scope parameter if it's not empty (PayProp assigns all scopes by default if excluded)
    if (cleanedScopes != null && !cleanedScopes.isEmpty()) {
        builder.queryParam("scope", cleanedScopes);
    }
    
    String fullUrl = builder.build().toUriString();
    
    System.out.println("🔗 FULL AUTHORIZATION URL: " + fullUrl);
    return fullUrl;
}
```

### **Token Exchange Implementation**

```java
public PayPropTokens exchangeCodeForToken(String authorizationCode, Long userId) throws Exception {
    System.out.println("🔄 STARTING TOKEN EXCHANGE:");
    System.out.println("   Token URL: " + tokenUrl);
    System.out.println("   Authorization URL (used): " + authorizationUrl);
    System.out.println("   API Base URL: " + environment.getProperty("payprop.api.base-url"));
    System.out.println("   Authorization Code: " + authorizationCode.substring(0, Math.min(20, authorizationCode.length())) + "...");
    System.out.println("   Client ID: " + clientId);
    System.out.println("   Client Secret: " + (clientSecret != null ? clientSecret.substring(0, Math.min(8, clientSecret.length())) + "..." : "NULL"));
    System.out.println("   Client Secret FULL: " + clientSecret);
    System.out.println("   Redirect URI: " + redirectUri);
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    
    MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
    requestBody.add("grant_type", "authorization_code");
    requestBody.add("code", authorizationCode);
    requestBody.add("client_id", clientId);
    requestBody.add("client_secret", clientSecret);
    requestBody.add("redirect_uri", redirectUri);

    System.out.println("🔍 TOKEN REQUEST BODY:");
    System.out.println("   grant_type: authorization_code");
    System.out.println("   code: " + authorizationCode.substring(0, Math.min(10, authorizationCode.length())) + "...");
    System.out.println("   client_id: " + clientId);
    System.out.println("   client_secret: " + (clientSecret != null ? clientSecret.substring(0, Math.min(4, clientSecret.length())) + "..." : "NULL"));
    System.out.println("   client_secret_length: " + (clientSecret != null ? clientSecret.length() : "NULL"));
    System.out.println("   client_secret_full: " + clientSecret);
    System.out.println("   redirect_uri: " + redirectUri);
    System.out.println("   redirect_uri_matches_config: " + redirectUri.equals(environment.getProperty("payprop.oauth2.redirect-uri")));

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

    try {
        System.out.println("🌐 MAKING TOKEN EXCHANGE REQUEST...");
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        
        // Process successful response...
        
    } catch (HttpClientErrorException e) {
        System.err.println("❌ HTTP CLIENT ERROR:");
        System.err.println("   Status Code: " + e.getStatusCode());
        System.err.println("   Response Body: " + e.getResponseBodyAsString());
        throw new RuntimeException("PayProp token exchange failed: " + e.getResponseBodyAsString(), e);
    }
}
```

### **Refresh Token Implementation (Fixed)**

```java
public PayPropTokens refreshToken() throws Exception {
    // ... load tokens ...
    
    MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
    requestBody.add("grant_type", "refresh_token");
    requestBody.add("refresh_token", currentTokens.getRefreshToken());
    requestBody.add("client_id", clientId);
    // NOTE: PayProp refresh token does NOT require client_secret per documentation
    
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
    
    // ... make request ...
}
```

---

## **Test Results**

### **Production Environment**
**URLs Used:**
- Authorization: `https://uk.payprop.com/api/oauth/authorize`
- Token: `https://uk.payprop.com/api/oauth/access_token`

**Credentials:**
- Client ID: `Propsk`
- Client Secret: `rphobpAUw1jgXI52` (16 characters, obtained fresh from PayProp 2025-09-10)

**Result**: ❌ **FAILED**
```json
{"error":"invalid_grant","error_description":"client_secret mismatch"}
```

### **Debug Output (Production)**
```
🔄 STARTING TOKEN EXCHANGE:
   Token URL: https://uk.payprop.com/api/oauth/access_token
   Authorization URL (used): https://uk.payprop.com/api/oauth/authorize
   API Base URL: https://uk.payprop.com/api/agency/v1.1
   Authorization Code: MTc1NzUxMDI4MS0xNTEx...
   Client ID: Propsk
   Client Secret: rphobpAU...
   Client Secret FULL: rphobpAUw1jgXI52
   Redirect URI: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback

🔍 TOKEN REQUEST BODY:
   grant_type: authorization_code
   code: MTc1NzUxMD...
   client_id: Propsk
   client_secret: rpho...
   client_secret_length: 16
   client_secret_full: rphobpAUw1jgXI52
   redirect_uri: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback
   redirect_uri_matches_config: true
```

---

## **Analysis**

### **Application Code Verification** ✅
- All parameters sent correctly per PayProp documentation
- No hardcoded URLs found in codebase
- Environment variables properly injected
- Request format matches PayProp specification exactly

### **Credential Verification** ✅
- Client secret obtained fresh from PayProp secret link on 2025-09-10
- 16 characters length (expected format)
- No hidden characters or encoding issues detected
- Redirect URI matches exactly

### **Request Verification** ✅
- Content-Type: `application/x-www-form-urlencoded` ✅
- POST method to correct endpoint ✅
- All required parameters present ✅
- Parameter values exactly as documented ✅

### **Potential Issues** 🤔
1. **PayProp System Issue**: Client secret not properly propagated in production
2. **Application Registration**: Client ID "Propsk" may have configuration issues in PayProp's system
3. **Environment Differences**: Production vs staging OAuth endpoints behave differently

---

## **Staging vs Production Differences**

| Aspect | Production | Staging |
|--------|------------|---------|
| Auth URL | `https://uk.payprop.com/api/oauth/authorize` | `https://ukapi.staging.payprop.com/oauth/authorize` |
| Token URL | `https://uk.payprop.com/api/oauth/access_token` | `https://ukapi.staging.payprop.com/oauth/access_tok` |
| Client Secret | `rphobpAUw1jgXI52` | `L7GJfqHWduV9IdU7` |
| Scopes | Empty (default all) | Specific scopes defined |
| URL Path | `/api/oauth/` | `/oauth/` |

**Note**: Staging URLs missing `/api` path and have truncated token URL, but were reported as "working variables."

---

## **Recommendations**

### **Immediate Actions**
1. **Contact PayProp Support** with this documentation
2. **Verify Client Registration** - Confirm "Propsk" is properly configured in production
3. **Test Staging Environment** to isolate if issue is environment-specific
4. **Remove Debug Logging** containing full client secret after testing

### **PayProp Support Query**
```
Subject: Client Secret Mismatch - OAuth Application "Propsk" Production Environment

Client ID: Propsk
Environment: Production (uk.payprop.com)
Error: "client_secret mismatch"
Client Secret: rphobpAUw1jgXI52 (obtained fresh from secret link 2025-09-10)
Redirect URI: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback

Request being sent matches your documentation exactly. 
Please verify client registration and secret validity.
```

### **Code Changes Made**
1. ✅ Fixed refresh token implementation (removed client_secret)
2. ✅ Enhanced debug logging with full request details
3. ✅ Added URL validation and environment variable verification

---

## **Files Modified**

- `PayPropOAuth2Service.java` - OAuth implementation with enhanced debugging
- `PayPropOAuth2Controller.java` - Controller handling OAuth callbacks
- `application.properties` - Environment variable configuration

**Total Investigation Time**: ~4 hours  
**Code Changes**: Minimal (mostly debugging enhancements)  
**Root Cause**: Likely PayProp configuration issue, not application code