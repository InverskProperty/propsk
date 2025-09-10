# PayProp OAuth2 Client Secret Mismatch Investigation

**Date**: September 10, 2025  
**Issue**: `{"error":"invalid_grant","error_description":"client_secret mismatch"}`  
**Client ID**: `Propsk`  
**Environment**: Production vs Staging comparison

---

## **Summary**

We're experiencing consistent "client_secret mismatch" errors with PayProp OAuth2 token exchange in production, despite using fresh credentials. Our staging environment works perfectly with identical application code, suggesting the issue may be environment-specific. We need PayProp's assistance to identify the root cause.

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

### **OAuth Controller Methods**

```java
@Controller
@RequestMapping("/api/payprop/oauth")
public class PayPropOAuth2Controller {
    
    @GetMapping("/authorize")
    public String initiateAuthorization(Authentication authentication) {
        if (!AuthorizationUtil.hasRole(authentication, "ROLE_MANAGER")) {
            return "redirect:/access-denied";
        }
        String state = UUID.randomUUID().toString();
        String authorizationUrl = oAuth2Service.getAuthorizationUrl(state);
        logger.info("🔐 Redirecting to PayProp authorization: {}", authorizationUrl);
        return "redirect:" + authorizationUrl;
    }
    
    @GetMapping("/callback")
    public String handleCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String error,
                                @RequestParam(required = false) String error_description,
                                @RequestParam(required = false) String state,
                                Model model, RedirectAttributes redirectAttributes,
                                Authentication authentication) {
        
        logger.info("📞 PayProp OAuth2 callback received - Code: {}, Error: {}", 
            code != null ? code.substring(0, Math.min(20, code.length())) + "..." : "null", error);

        if (error != null) {
            logger.error("❌ OAuth2 authorization failed: {}", error);
            redirectAttributes.addFlashAttribute("error", 
                "PayProp authorization failed: " + (error_description != null ? error_description : error));
            return "redirect:/api/payprop/oauth/status";
        }

        if (code == null) {
            logger.error("❌ No authorization code received");
            redirectAttributes.addFlashAttribute("error", "No authorization code received from PayProp");
            return "redirect:/api/payprop/oauth/status";
        }

        try {
            // Extract user ID from authentication
            Long userId = authenticationUtils.getLoggedInUserId(authentication);
            
            // Call token exchange service
            PayPropTokens tokens = oAuth2Service.exchangeCodeForToken(code, userId);
            
            logger.info("✅ OAuth2 tokens obtained successfully for user {}", userId);
            redirectAttributes.addFlashAttribute("success", "PayProp connection established successfully!");
            return "redirect:/api/payprop/oauth/status";
            
        } catch (Exception e) {
            logger.error("❌ Token exchange failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Token exchange failed: " + e.getMessage());
            return "redirect:/api/payprop/oauth/status";
        }
    }
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

### **Test 1: Production Environment**
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

#### **Equivalent cURL Command (Production)**
```bash
curl -X POST "https://uk.payprop.com/api/oauth/access_token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=MTc1NzUxMDI4MS0xNTEx..." \
  -d "client_id=Propsk" \
  -d "client_secret=rphobpAUw1jgXI52" \
  -d "redirect_uri=https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback" \
  -v
```

#### **Debug Output (Production)**
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

### **Test 2: Production URL Path Variation**
**URLs Used:**
- Authorization: `https://uk.payprop.com/api/oauth/authorize`
- Token: `https://uk.payprop.com/oauth/access_token` (removed `/api/` to match staging pattern)

**Credentials:**
- Client ID: `Propsk`
- Client Secret: `rphobpAUw1jgXI52` (same production secret)

**Result**: ❌ **FAILED - 404 ERROR**
```html
HTTP 404 "Page not found" HTML page from PayProp's website
```

**Finding**: Production PayProp does NOT have `/oauth/access_token` endpoint (without `/api/`). 
The correct production URL is confirmed as `https://uk.payprop.com/api/oauth/access_token`.

---

### **Test 3: Production with Explicit Scopes**
**URLs Used:**
- Authorization: `https://uk.payprop.com/api/oauth/authorize`  
- Token: `https://uk.payprop.com/api/oauth/access_token` (correct production URL)

**Credentials:**
- Client ID: `Propsk`
- Client Secret: `rphobpAUw1jgXI52` (same production secret)
- Scopes: `"read:export:beneficiaries read:export:tenants read:export:properties"` (explicit scopes like staging)

**Result**: ❌ **FAILED - SAME ERROR**
```json
{"error":"invalid_grant","error_description":"client_secret mismatch"}
```

#### **Debug Output (Production with Scopes)**
```
🔧 BUILDING AUTHORIZATION URL:
   Base URL: https://uk.payprop.com/api/oauth/authorize
   Client ID: Propsk
   Redirect URI: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback
   Scopes (raw): read:export:beneficiaries read:export:tenants read:export:properties
   State: 04a45706-0140-4293-a65b-300648617c85
   Scopes (cleaned): read:export:beneficiaries read:export:tenants read:export:properties
🔗 FULL AUTHORIZATION URL: https://uk.payprop.com/api/oauth/authorize?response_type=code&client_id=Propsk&redirect_uri=https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback&state=04a45706-0140-4293-a65b-300648617c85&scope=read:export:beneficiaries read:export:tenants read:export:properties

🔄 STARTING TOKEN EXCHANGE:
   Token URL: https://uk.payprop.com/api/oauth/access_token
   Authorization URL (used): https://uk.payprop.com/api/oauth/authorize
   API Base URL: https://uk.payprop.com/api/agency/v1.1
   Authorization Code: MTc1NzUxNTUwMy04Mjcz...
   Client ID: Propsk
   Client Secret: rphobpAU...
   Client Secret FULL: rphobpAUw1jgXI52
   Redirect URI: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback

🔍 TOKEN REQUEST BODY:
   grant_type: authorization_code
   code: MTc1NzUxNT...
   client_id: Propsk
   client_secret: rpho...
   client_secret_length: 16
   client_secret_full: rphobpAUw1jgXI52
   redirect_uri: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback
   redirect_uri_matches_config: true

❌ HTTP CLIENT ERROR:
   Status Code: 400 BAD_REQUEST
   Response Body: {"error":"invalid_grant","error_description":"client_secret mismatch"}
```

**Finding**: Adding explicit scopes (like staging) does NOT resolve the issue. Same client_secret mismatch error persists.

---

### **Test 4: Staging Environment**
**URLs Used:**
- Authorization: `https://ukapi.staging.payprop.com/api/oauth/authorize`
- Token: `https://ukapi.staging.payprop.com/api/oauth/access_token`

**Credentials:**
- Client ID: `Propsk`
- Client Secret: `L7GJfqHWduV9IdU7` (16 characters, staging credentials)

**Result**: ✅ **SUCCESS**
```json
{
  "access_token": "MTc1NzUxMjM0OC01ODUwNDktMC4zMjQ0NjQ3MTk1MTk4OTItMzd6U1JlRmFFVEIwSk8zVzRLWDl0ZGdkUG52Nm9S",
  "expires_in": 86400,
  "refresh_token": "MTc1NzUxMjM0OC01ODUxNTgtMC4xNTA0MTkzMzEwNi1rTEYwaHIyU2JDOFlQQ3M5SmtZeHlIMzZ3Z3ZhOWc=",
  "scopes": [],
  "token_type": "Bearer"
}
```

#### **Equivalent cURL Command (Staging - Working)**
```bash
curl -X POST "https://ukapi.staging.payprop.com/api/oauth/access_token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=MTc1NzUxMjM0Ny05OTY5..." \
  -d "client_id=Propsk" \
  -d "client_secret=L7GJfqHWduV9IdU7" \
  -d "redirect_uri=https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback" \
  -v
```

#### **Debug Output (Staging)**
```
🔄 STARTING TOKEN EXCHANGE:
   Token URL: https://ukapi.staging.payprop.com/api/oauth/access_token
   Authorization URL (used): https://ukapi.staging.payprop.com/api/oauth/authorize
   API Base URL: https://ukapi.staging.payprop.com/api/agency/v1.1
   Authorization Code: MTc1NzUxMjM0Ny05OTY5...
   Client ID: Propsk
   Client Secret: L7GJfqHW...
   Client Secret FULL: L7GJfqHWduV9IdU7
   Redirect URI: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback

🔍 TOKEN REQUEST BODY:
   grant_type: authorization_code
   code: MTc1NzUxMj...
   client_id: Propsk
   client_secret: L7GJ...
   client_secret_length: 16
   client_secret_full: L7GJfqHWduV9IdU7
   redirect_uri: https://spoutproperty-hub.onrender.com/api/payprop/oauth/callback
   redirect_uri_matches_config: true

📥 TOKEN RESPONSE:
   Status Code: 200 OK
   Response Body: {access_token=MTc1NzUxMjM0OC01ODUwNDktMC4zMjQ0NjQ3MTk1MTk4OTItMzd6U1JlRmFFVEIwSk8zVzRLWDl0ZGdkUG52Nm9S, 
                  expires_in=86400, 
                  refresh_token=MTc1NzUxMjM0OC01ODUxNTgtMC4xNTA0MTkzMzEwNi1rTEYwaHIyU2JDOFlQQ3M5SmtZeHlIMzZ3Z3ZhOWc=, 
                  scopes=[], 
                  token_type=Bearer}

✅ TOKENS OBTAINED AND SAVED SUCCESSFULLY!
   Access Token: MTc1NzUxMjM0OC01ODUw...
   Token Type: Bearer
   Expires At: 2025-09-11T13:52:29.267923765
   Has Refresh Token: true
```

#### **API Connection Test (Staging)**
```
✅ PayProp API connection verified!
   Connected as: John Naylor (sajidkazmi@propsk.com)
   User type: agency
   Is admin: true
   Agency: TAKEN: Propsk
   Available scopes: create:attachment:upload, create:entity:adhoc-invoice, create:entity:adhoc-payment, 
                    create:entity:beneficiary, create:entity:invoice, create:entity:payment, 
                    create:entity:property, create:entity:tags, create:entity:tenant, 
                    create:maintenance:messages, create:maintenance:tickets, create:reminders:entity, 
                    create:transactions:credit-note, create:transactions:debit-note, delete:entity:tags, 
                    read:agency:global-beneficiaries, read:attachment:download, read:attachment:list, 
                    read:entity:beneficiary, read:entity:invoice, read:entity:payment, read:entity:property, 
                    read:entity:tags, read:entity:tenant, read:export:beneficiaries, read:export:invoices, 
                    read:export:payments, read:export:properties, read:export:tenants, read:invoices:categories, 
                    read:maintenance:categories, read:maintenance:messages, read:maintenance:tickets, 
                    read:meta:webhook-failures, read:meta:webhooks, read:payments:categories, 
                    read:reminders:entity, read:report:all-payments, read:report:beneficiary-balances, 
                    read:report:icdn, read:transactions:frequencies, read:transactions:tax, 
                    update:entity:property, update:entity:tags, update:maintenance:tickets, 
                    update:meta:webhooks, update:reminders:entity
🔍 PayProp API connection test: SUCCESS
```

---

## **Analysis**

### **What We've Confirmed** ✅
- All parameters sent correctly per PayProp documentation
- No hardcoded URLs found in codebase
- Environment variables properly injected
- Request format matches PayProp specification exactly
- Client secret obtained fresh from PayProp secret link on 2025-09-10
- 16 characters length (expected format)
- Redirect URI matches exactly
- Content-Type: `application/x-www-form-urlencoded` ✅
- POST method to correct endpoint ✅
- All required parameters present ✅

### **Staging vs Production Behavior**
- **Staging**: Same client ID, different secret → **Works perfectly**
- **Production**: Same client ID, different secret → **"client_secret mismatch"**
- **Identical application code** used in both environments

### **Possible Areas to Investigate** 🤔
1. Production client secret validity or configuration
2. Client ID "Propsk" registration differences between environments
3. Production vs staging OAuth endpoint behavior differences

---

## **Staging vs Production Differences**

| Aspect | Production | Staging |
|--------|------------|---------|
| Auth URL | `https://uk.payprop.com/api/oauth/authorize` | `https://ukapi.staging.payprop.com/oauth/authorize` |
| Token URL | `https://uk.payprop.com/api/oauth/access_token` | `https://ukapi.staging.payprop.com/oauth/access_tok` |
| Client Secret | `rphobpAUw1jgXI52` | `L7GJfqHWduV9IdU7` |
| Scopes | Empty (default all) | Specific scopes defined |
| URL Path | `/api/oauth/` | `/oauth/` |
| URL Structure Validation | ✅ Both `/api/oauth/` paths exist | ✅ Both `/oauth/` paths exist |

**Key Findings**:
- Production requires `/api/oauth/` paths (tested: `/oauth/` returns 404)
- Staging uses `/oauth/` paths (no `/api/` prefix)
- Different URL structures between environments confirmed
- Same client_secret format (16 characters) in both environments

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

Our request matches your documentation exactly, and identical code works in staging.
Could you help verify our production client registration and secret validity?

See attached debug logs showing exact parameters sent.
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
**Status**: Need PayProp assistance to resolve production environment issue