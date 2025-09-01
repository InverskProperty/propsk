```yaml
---
basePath: /api/agency/v1.1
consumes:
- application/json
definitions:
  TenantCreditCheck:
    discriminator: identity_type
    properties:
      identity_type:
        type: string
    required:
    - identity_type
    type: object
  business:
    allOf:
    - $ref: '#/definitions/TenantCreditCheck'
    - $ref: '#/definitions/tenant_cc_base_params'
    - properties:
        business_name:
          example: 'Company Co.'
          maxLength: 35
          minLength: 4
          type: string
        registration_number:
          description: Company registration number.
          example: '1999/000701/07'
          maxLength: 50
          minLength: 6
          pattern: ^\d{2,}/\d{4,}/\d{2,}$
          type: string
      required:
      - business_name
      - registration_number
  passport:
    allOf:
    - $ref: '#/definitions/TenantCreditCheck'
    - $ref: '#/definitions/tenant_cc_base_params'
    - $ref: '#/definitions/tenant_cc_south_african_id_passport_base_params'
    - properties:
        date_of_birth:
          example: '1991-12-24'
          format: date
          maxLength: 10
          type: string
        gender:
          enum:
          - male
          - female
          type: string
        passport_country:
          description: 2-letter country code of the passport.
          example: 'CH'
          maxLength: 2
          minLength: 2
          pattern: ^[A-Z]{2}$
          type: string
        passport_number:
          maxLength: 17
          minLength: 3
          type: string
      required:
      - gender
      - date_of_birth
      - passport_number
      - passport_country
  south_african_id:
    allOf:
    - $ref: '#/definitions/TenantCreditCheck'
    - $ref: '#/definitions/tenant_cc_base_params'
    - $ref: '#/definitions/tenant_cc_south_african_id_passport_base_params'
    - properties:
        id_number:
          description: South African ID number.
          example: '2001015800085'
          maxLength: 13
          minLength: 13
          pattern: ^\d+$
          type: string
      required:
      - id_number
  tenant_cc_base_params:
    properties:
      email:
        example: 'mail@email.com'
        format: email
        maxLength: 50
        type: string
      mobile_number:
        example: '27825231234'
        maxLength: 15
        pattern: ^[1-9]\d+$
        type: string
      tenant_id:
        description: Associate credit check with tenant entity on PayProp platform.
        example: 'zJBv2E5aJQ'
        maxLength: 32
        type: string
    type: object
  tenant_cc_south_african_id_passport_base_params:
    properties:
      first_name:
        description: First name of the tenant.
        example: 'John'
        maxLength: 35
        minLength: 2
        type: string
      last_name:
        description: Last name of the tenant.
        example: 'Smith'
        maxLength: 35
        minLength: 2
        type: string
      monthly_rent:
        description: The property's monthly rental amount is used in numerous calculations.
          Incorrect data will affect the accuracy of the report.
        example: '10000.00'
        maximum: 1000000
        minimum: 10
        multipleOf: 0.01
        type: number
      net_monthly_income:
        description: The applicant's net monthly income is used in numerous calculations.
          Incorrect data will affect the accuracy of the report.
        example: '50000.00'
        maximum: 1000000
        minimum: 1000
        multipleOf: 0.01
        type: number
    required:
    - first_name
    - last_name
    - monthly_rent
    - net_monthly_income
    type: object
host: uk.payprop.com
info:
  description: |
    ## 1. Introduction

    The goal of the API is to enable straightforward consumption and manipulation of data from the [PayProp](https://www.payprop.com) platform for use elsewhere. This API is a work in progress, new operations will be added as necessary.


    The API specification is defined using [OpenAPI](https://openapis.org/) so you can use the raw specification to automatically generate client code for your framework should you wish to take that approach. The API v1.1 specification can be downloaded from this documentation page.

    ### 1.1 Restrictions

    * You will need an Agency or Agent account on PayProp to use the PayProp API

    * If you wish to use PayProp for third party login then you must request a __Client ID__ and __Client Secret Key__


    <p>&nbsp;</p>


    ## 2. Authentication and Security

    Access to the API is governed by either:

    * __API Key__ - an API key that is linked to your PayProp account


    Or in the case of OAuth v2.0 access:

    * __Client ID__ - you will be given one of these for each Application that will access the API

    * __Client Secret Key__ - you will be given one of these for use in conjunction with the __Client ID__

    * __Access Token__ - one per user, when they explicitly authorize your application to access their PayProp account


    When you perform operations on behalf of a user, using an Access Token, you will need to gain authorization from the user of your application to access their PayProp account. This is done via a standard OAuth v2.0 flow, detailed in section 2.2 below.


    The PayProp API supports secure connections over SSL only. Make sure your requests are made over __https__ and not http. The API URL is determined by the country you are working with.


    ### 2.1 API Key or Access Tokens?

    If you want to access your own account using the API then you should create an API key in your account settings in PayProp. You can create as many API keys as necessary, and each key can have its own set of permissions (which can only be a subset of your own account permissions).


    This enables you to create, for example, an API key that has read only access to your data; or an API key that can only access tenant data, and so on. If you want to perform actions *on behalf of another account that is not your own* then you will need to use the OAuth v2.0 flow as documented below in section 2.2.


    ### 2.1.1 Using API Keys

    You MUST provide the API key in an HTTP Authorization request header like so:

            Authorization: APIkey MTYzMTg2MDY3MS1wWikuVzsjKnlGJFFKb0RYWCwhe1sjdUBGZ0Y3RT9QPw==


    ### 2.1.2 Using Access Tokens

    You MUST provide the Access Token in an HTTP Authorization request header like so:

            Authorization: Bearer MTYzMTg2MDY3MS1wWikuVzsjKnlGJFFKb0RYWCwhe1sjdUBGZ0Y3RT9QPw==


    ### 2.2 OAuth v2.0 Flow (Auth Code Grant)

    The PayProp API can use the OAuth v2.0 specification for user authorization, with simple Bearer authentication, as document in [section 4.1 of RFC6749](http://tools.ietf.org/html/rfc6749#section-4.1). After going through the authorization / authentication flow, you will end up with one Access Token per user (only valid for the registered application that requested it). You will use this access token whenever you make a call to the API.


    You will need to request a Client ID and Client Secret Key for accessing the API. The Client Secret Key MUST be treated as secret data - you should encrypt it when stored and MUST NOT reveal it to any other user or application. If the Client Secret Key is compromised you should contact PayProp support to have any associated Access Tokens revoked and a new Client Secret Key generated for your application.


    We're going to assume that you've gotten your Client Secret Key, and a Client ID to work with. This example uses example strings for the Client Secret Key, Client ID, Authorization Code and Access Token.

    1. Your application needs permission from a user to access and/or manipulate their account data, so you send them to the PayProp API authorization page. If the scope parameter is excluded, your access token will be assigned all scopes by default:

            https://uk.payprop.com/api/oauth/authorize?response_type=code&client_id=YourAppClientID \
            &redirect_uri=https%3A%2F%2Fyourapp.com%2Fcallback&scope=read:export:beneficiaries%20read:export:tenants

        Note that by default the user will be redirected to full PayProp application to sign in and confirm the requested scopes. To display a minimal layout without header and footer you can provide an optional URL parameter __oauth_hidenav=1__ when initiating authorization request.

    2. If the user is not currently logged in at PayProp they will be prompted to login

    3. When logged in, the user is presented with a dialog where they allow or deny access to their PayProp account for your application. They will then be redirected back to the URL provided in the original request for an Authorization Code:

            https://yourapp.com/callback?code=43423d76123a1124f2

    4. Your application makes a background call to the access token url with the Authorization Code acquired in the previous step, along with both your application's Client ID and your Client Secret Key. You MUST do this with a POST request, as this will prevent your Client Secret Key being logged in any intermediary proxy server access logs. The __redirect_uri__ value MUST be URL encoded, and MUST match the callback url you supplied when requesting an Authorization Code. The __grant_type__ MUST be provided and set to "authorization\_code"

            POST /api/oauth/access_token HTTP/1.1
            Host: uk.payprop.com
            Content-Type: application/x-www-form-urlencoded

            code=43423d76123a1124f2&client_id=YourAppClientID&client_secret=YourAppClientSecret& \
            redirect_uri=https%3A%2F%2Fyourapp.com%2Fcallback&grant_type=authorization_code

    5. The PayProp API will return a JSON response containing details of the Access Token:

            {
                "token_type": "Bearer",
                "expires_in": 3600,
                "access_token": "MTQyNjUxOTM0MC01OTA0N3NDY0MDkzMDgxOTI1LW5FYzVJdVJOMFhGd0NLazFxbk1rejlRMzRXdE9NNg==",
                "refresh_token": "MTQyNjUxOTM0MC01OTA3k4MjkzMzIyNzAzMTktaUFHcFRGMERBcEFHYWp4Z1NJdGVUdWxLT2dKelcy"
            }

    6. Store the Access Token and provide it in the request headers to authorize a call to the PayProp API whenever you need to access or manipulate data of the user that is associated with the token.

            GET /api/agency/v1.1/meta/me HTTP/1.1
            Host: uk.payprop.com
            Authorization: Bearer MTQyNjUxOTM0MC01OTA0N3NDY0MDkzMDgxOTI1LW5FYzVJdVJOMFhGd0NLazFxbk1rejlRMzRXdE9NNg==

    7. When the Access Token expires (the validity period, in seconds, of the Access Token is returned in the "expires\_in" field from step 5) you can use the Refresh Token to get a new Access Token. The Refresh Token will never expire, so there is no need to do this until the PayProp API needs to be accessed. The __grant_type__ MUST be provided and set to "refresh\_token". To use the Refresh Token you can use either a POST or a GET request (POST example shown below). The response JSON will be the same as in step 5, containing a new Access Token and a new Refresh Token.

            POST /api/oauth/access_token HTTP/1.1
            Host: uk.payprop.com
            Content-Type: application/x-www-form-urlencoded

            client_id=YourAppClientID&refresh_token=MTQyNjUxOTM0MC01OTA3NDktMC45Mjk4MjkzMzI&grant_type=refresh_token

    ### 2.2.1 The state parameter

    The PayProp API supports usage of the optional __state__ parameter. This is a random value generated and sent by your application on the initial request for authorization.

    1. Your application generates a unique value and includes it on the URL during the initial request for authorization.

            https://uk.payprop.com/api/oauth/authorize?response_type=code&client_id=YourAppClientID \
            &state=77787881293891238127847812&redirect_uri=https%3A%2F%2Fyourapp.com%2Fcallback

    2. PayProp will process the request and redirect to the specified callback URL with the authorization code as well as the __state__ parameter.

            https://yourapp.com/callback?code=43423d76123a1124f2&state=77787881293891238127847812

    3. You application should ensure that __state__ parameter received in the callback URL matches the original value sent on the initial request before requesting an access token.

    ### 2.3 OAuth v2.0 Flow (Implicit Grant)

    The PayProp API can use the OAuth v2.0 specification for user authorization, with simple Bearer authentication, as document in [section 4.2 of RFC6749](http://tools.ietf.org/html/rfc6749#section-4.2). After going through the authorization / authentication flow, you will end up with one Access Token per user (only valid for the registered application that requested it). You will use this access token whenever you make a call to the API.


    You will need to request a Client ID and provide a Redirect URI.


    We're going to assume that you've gotten your Client ID to work with and have provided a Redirect URI. This example uses example strings for the Client Secret Key, Client ID, Authorization Code and Access Token.

    1. Your application needs permission from a user to access and/or manipulate their account data, so you send them to the PayProp API authorization page. If the scope parameter is excluded, your access token will be assigned all scopes by default:

            https://uk.payprop.com/api/oauth/authorize?client_id=YourAppClientID \
            &response_type=token&redirect_uri=http://redirect-to.com/callback

    2. If the user is not currently logged in at PayProp they will be prompted to login.

    3. When logged in the user is presented with a dialog, where they allow or deny access to their PayProp account for your application. They will then be redirected back to the Redirect URI provided in the original request for an Access Token with the details in the fragment part of the URL:

             http://redirect-to.com/callback# \
             access_token=MTQyNjUxOTM0MC01OTA0N3NDY0MDkzMDgxOTI1LW5FYzVJdVJOMFhGd0NLazFxbk1rejlRMzRXdE9NNg== \
             &token_type=bearer&expires_in=315360000

    4. Store the Access Token and provide it in the request headers to authorize a call to the PayProp API whenever you need to access or manipulate data of the user that is associated with the token.

            GET /api/agency/v1.1/meta/me HTTP/1.1
            Host: uk.payprop.com
            Authorization: Bearer MTQyNjUxOTM0MC01OTA0N3NDY0MDkzMDgxOTI1LW5FYzVJdVJOMFhGd0NLazFxbk1rejlRMzRXdE9NNg==

    7. When the Access Token expires (the validity period, in seconds, of the Access Token is returned in the "expires\_in" field from step 3) you must request the user to revalidate your client and run through the process again. Note that access tokens have very long validity periods to prevent the user having to revalidate your client application frequently. A user can, however, revoke access to your client application at any time.


    <p>&nbsp;</p>


    ## 3. Pagination

    For operations that return multiple entities pagination is possible to prevent excess server load and large response bodies. Any operations that supports pagination will return a __Link__ header as per [RFC5988](https://tools.ietf.org/html/rfc5988) when there is more data available. An example for the agencies operation:

    <pre>
            &lt;https://uk.payprop.com/api/agency/v1.1/export/tenants?rows=2&page=1 >; rel="prev", \
            &lt;https://uk.payprop.com/api/agency/v1.1/export/tenants?rows=2&page=2 >; rel="last", \
            &lt;https://uk.payprop.com/api/agency/v1.1/export/tenants?rows=2&page=1 >; rel="first"
    </pre>

    This allows you to navigate to the `first`, `last`, `prev`, and `next` URLs without having to construct your own URLs. The `prev` and `next` links may not always be present, in the case of being at the start or end of the dataset. When the response contains all of the information available for the operation then the __Link__ header will be empty.


    You are free to change the values of the `rows` and `page` parameters to retrieve smaller or larger response bodies, however there is a restriction on the number of rows to prevent too large a response body.

    Paginated endpoints include a `pagination` object within the JSON response.

    <pre>
        "pagination": {
            "page": 1,
            "rows": 1,
            "total_rows": 1,
            "total_pages": 1
        }
    </pre>

    <p>&nbsp;</p>


    ## 4. HTTP Methods

    The type of request you make for an operation depends on the HTTP Method you use to make the call.

    * __GET__ to retrieve data

    * __POST__ to add data

    * __PUT__ to update data

    * __DELETE__ to delete data

    The API makes use of a combination of HTTP Response Codes and a JSON Response Body to let you know whether your request was successful or not:

    * __200__ Successful

    * __400__ Bad or malformed request

    * __401__ Not authorized (bad credentials)

    * __403__ Forbidden (missing privilege)

    * __404__ Entity or operation does not exist

    * __500__ Internal Server Error, please contact support

    * __501__ Not Implemented, the resource is not available for the user type

    * __503__ Service Unavailable, the service is down for maintenance


    Check the Response Body, which should always be in JSON format, for more details.

    Please note that failed API calls return a JSON response with `status` and `errors` keys, as shown below. Do not use the `message` response to test your code as the message may change.

        {
            "errors": [
              {
                "message" : "Could not find entity based on given customer ID"
              }
            ],
            "status": 404
        }

    <p>&nbsp;</p>


    ## 5. {external_id} fields

    All ID fields are encoded. They will be a minimum length of 10 chars, max length of 32. They will be alphanumeric only, so in the regexp range `[A-Za-z0-9]`. This means in operations such as `/entity/{external_id}` the URL will look something like `/entity/ghH3ifSd9d` rather than `/entity/12345`. For more information see [hashids](http://hashids.org/).


    <p>&nbsp;</p>


    ## 6. Search parameters

    For GET operations you can provide search parameters as URL query params (`key=value`). Note that providing several different parameters will restrict the search to finding an entity that matches *all* the parameters. By default the search will be specific, that is to say a search for "foo" will only search for exactly "foo".


    <p>&nbsp;</p>


    ## 7. Terms of Use

    ### 7.1 Use of API

    The PayProp application programming interface ("__API__") is provided by PayProp Limited, (herein "__PayProp__"), a company incorporated in the United Kingdom under registration number 05405100. The use of the API is restricted to the Customer (as defined in the Customer Agreement)  who has entered into and maintains a valid customer agreement ("__Customer Agreement__") in writing with a PayProp affiliate (herein "__PayProp Affiliate__"), who has accepted and agreed to be bound to the terms set out in these API Terms of Use, as amended by PayProp from time to time ("__Terms__"), and to whom PayProp, in its sole discretion, has issued unique authentication credentials (herein "__Authentication Credentials__") for use with the API.

    The use of the API is an extension of the Service (as defined in the Customer Agreement) rendered by PayProp and/or the PayProp Affiliate in terms of the Customer Agreement to Customer ("__Customer__", "__You__", "__Your__") and governed by the terms set out therein, as supplemented by these Terms.  In the event of any conflict or inconsistency between these Terms and the Customer Agreement, these Terms will prevail with regard to the subject matter of these Terms.  By using the API, PayProp is irrevocably authorised to carry out any instructions given via the API that have been authenticated with the Customer's Authentication Credentials.  For clarity, this authority applies to any use of the Customer's Authentication Credentials, however arising.

    PayProp reserves the right to amend these Terms and update the API in its sole discretion from time to time. PayProp agrees to keep the API specifications substantially updated with such technical documentation as may be useful in its view to describe the functional and technical specifications of the API from time to time, notably in respect of possible breaking changes.

    ### 7.2 Integration and development

    It is Your sole responsibility to integrate Your system with the API in a secure and reliable manner. Any assistance requested from PayProp shall be subject to the entering into of a separate professional services agreement in writing. You may request PayProp to issue temporary Authentication Credentials for a period of up to 90 (ninety) days that limit the functionality of API transactions to a development sandbox with dummy data for purposes of integration testing.  You understand and agree that it is Your obligation to remain properly and securely integrated with the API, as amended from time to time.

    PayProp will not support legacy versions of the API, but will continue to make legacy versions available for use upon releasing updated versions of the API.  Prior to releasing any major update to the API, and upon request from an API user, PayProp will however endeavour to make available a development sandbox with dummy data for purposes of integration testing. PayProp reserves the right to clean, change, or remove legacy versions of the API.

    ### 7.3 Service charges

    The use of the API is subject to payment of PayProp's additional prevailing service charges from time to time, which may be changed as set out in the Customer Agreement. However, any changes to PayProp's additional prevailing service charges, including any payments in relation to the use of the API, will be communicated to all PayProp clients with at least 30 days prior notice.

    ### 7.4 Intellectual property

    All intellectual property rights (inclusive of copyright) pertaining to the API and all related specifications, user and other documentation provided by PayProp to You shall belong to and remain vested in PayProp. While You remain entitled to use the API as set out in these Terms, You are granted a non-exclusive, non-transferable and non-sublicensable licence to use and allow Users (as defined in the Customer Agreement) to use the API for [the purposes of accessing and using the Website (as defined in the Customer Agreement)] in accordance with the Terms, and no other purpose. All rights not expressly granted to You in terms of these Terms shall remain reserved to PayProp.

    ### 7.5 Confidentiality and security

    You agree to keep confidential and not to disclose the API or any information which may be provided to You pertaining to the API, including authentication credentials, security measures, vulnerabilities, specifications and methods of operation (collectively herein "__Confidential Information__"). You agree to protect (and ensure Users protect) the Confidential Information from unauthorised use, access or disclosure and to delete and immediately destroy such Confidential Information in Your possession upon the termination of Your entitlement to use the API. You agree to promptly report any security deficiencies in, or intrusions to Your system or to PayProp via the API, to api@payprop.com.

    ### 7.6 Restrictions on use

    You agree not to use the API to circumvent the provisions of the Customer Agreement by undertaking any action that would constitute a breach of the Customer Agreement, to operate a bureau service or syndicate access to the API, engage in any unlawful or illegal activity, to disrupt, impair or overburden our network or operations, to circumvent, disable or otherwise interfere (or attempt to interfere) with any features or functionality of the API or restrictions imposed on You by these Terms, or use the API and any information or data (including personal data) derived from it in an unlawful and/or illegal manner.

    You further agree not to reverse-engineer, decompile or reverse assemble the API (save as may be expressly permitted under applicable law), use the API to operate services that, whether visually or functionally, replicates a substantial number of features and/or the overall experience of the PayProp platform, sell or sublicense Your access to the API to a third party without our prior written approval or to permit any third party to use the API, impersonate any other user while using the API or to log in to the API with false information; and impose any terms on users of Your services that are inconsistent with these Terms.

    All handling and processing of data will be done in accordance with the PayProp Terms of Use, the PayProp Privacy Policy, and any other contractual agreement between PayProp and its clients.

    ### 7.7 Termination and suspension

    Your access to the API shall automatically terminate when the Customer Agreement terminates for any reason. PayProp shall be entitled to change, terminate or suspend Your access to the API and/ or other PayProp intellectual property rights at any time and for any reason, without notice. Without limiting the foregoing, we may limit Your access to the API if, in PayProp's sole discretion, it may negatively affect PayProp's service or PayProp's ability to provide its service.

    ### 7.8 Security

    You are notified that it may be a criminal act to circumvent, disable or otherwise interfere (or attempt to do so) with our security measures and You agree not to directly or indirectly do so or allow another person to do so.

    ### 7.9 Disclaimers and limitation of liability

    In addition to the disclaimers and limitations of liability set out in the Customer Agreement and notwithstanding anything to the contrary, You agree that the API is made available "as is", without any warranty, representation, undertaking or implied terms of any kind. To the maximum extent permitted under applicable law, PayProp and the PayProp Affiliate shall not be liable to the Customer for any claims, cost, loss, damage or expense, however arising.  You agree to indemnify PayProp and the PayProp Affiliate from any claims, cost, loss, damage or expense it suffers or incurs as a consequence of any breach by You of Your obligations under these Terms.

    ### 7.10 General

    These Terms, together with the Customer Agreement, constitute the whole agreement between You and us regarding Your use of the API; no other agreements, terms, representations or warranties relating to the API shall be binding on us unless reduced to writing, and you have not relied on any other agreements, terms, representations, warranties in entering into these Terms.

    These Terms shall be governed by the laws of England and Wales. You agree that the courts of England and Wales shall have exclusive jurisdiction to hear any disputes that may arise from these Terms.


    <p>&nbsp;</p>


    ## 8. Operations

    ### 8.1 Appending JSON content

    Please be advised that you must be able to handle the addition of new JSON content fields. The addition of new JSON content fields will not result in API version increment.

    Having existing JSON schema

        {
            "first_name": "John"
        }

    addition of `last_name` and `age` fields must be handled by the API consumer.

        {
            "first_name": "John",
            "last_name": "Bow",
            "age": 20
        }

    ### 8.2 Throttling

    To prevent the API from being overwhelmed, you are allowed a maximum of 5 requests per second. Exceeding this limit will result in the failure of all your subsequent requests, for the next 30 seconds.

    Furthermore, you will be met with the following error

        {
          "errors":
            [
              {
                "message": "Too many requests"
              }
            ],
          "status": 429
        }


    <p>&nbsp;</p>


    ## 9. Webhooks

    Webhooks are automated HTTP requests that are sent to consumer callback URLs when triggered by events on the PayProp platform. This enables PayProp to notify another system when an event occurs on the platform and to send relevant payload data describing the event.

    All PayProp webhooks are sent via HTTP POST request and contain a JSON data payload. PayProp will attempt to group multiple events from the same platform account where possible and send them as a JSON batch payload containing the `events` key.

    We will attempt to resend failed webhooks to the consumer endpoint a fixed amount of times within fixed intervals.

    By default, a webhook notification will not be sent to the webhook callback URL defined by the API consumer. To send an API action webhook notification to your webhook callback URL, please add the `?send_webhook=true` parameter to the API resource URL, as shown in the example below.

        POST https://uk.payprop.com/api/agency/v1.1/entity/payment?send_webhook=true

    ### 9.1 Creating a webhook callback URL

    If you are accessing the PayProp API via an API key as an agent or agency then go to https://uk.payprop.com/c/settings/api and provide your webhook callback URL in the 'Webhook URL' field found on the page.

    Third parties accessing the PayProp API via OAuth v2.0 on behalf of an agent or agency should contact support at api@payprop.com to create a webhook callback URL for your OAuth v2.0 client.

    ### 9.2 Signing webhook content

    You can optionally sign the webhook events sent to your webhook callback URL by providing a secret key. If you choose to sign webhook content, PayProp will include the signature with each webhook event in the request header. This will allow you to verify that the events sent to your webhook callback URL originated from PayProp by verifying the signature.

    When a secret key is provided, two additional headers will be sent with the webhook content - __X-PayProp-Webhook-Signature__ and __X-PayProp-Webhook-Timestamp__. You can then use the webhook payload and the values above from the headers to determine the validity of the webhook payload.

    To determine webhook validity you will need to extract the value from the __X-PayProp-Webhook-Timestamp__ header and concatenate it with the raw request payload using "." _(DOT)_ character - `TIMESTAMP.WEBHOOK_JSON_PAYLOAD`. You will then need to compute HMAC with the SHA256 hash function using the secret key and compare it to the value from __X-PayProp-Webhook-Signature__ header. If the values match, the webhook content is valid.

    ### 9.3 Webhook event content

    Each webhook event content will include three keys describing the event that took place on the platform.

    <p>To view webhook JSON examples <a id="toggle-accordions" href="#">please click here.</a><p>
    <div id="accordions-container"></div>

      * `action` - the action that took place on the platform related to the entity
          * `create` - new entity was created
          * `update` - existing entity was updated
          * `delete` - existing entity was deleted
          * `restore` - deleted entity was restored
      * `type` - the entity type that was affected by the action on the platform
          * `beneficiary` - beneficiary entity on the platform
              * Actions: `create`, `update`, `restore`
          * `property` - property entity on the platform
              * Actions: `create`, `update`, `restore`
          * `tenant` - tenant entity on the platform
              * Actions: `create`, `update`, `restore`
          * `payment` - reconciled incoming payment
              * Actions: `create`, `update`
          * `invoice_rule` - recurring invoice rule
              * Actions: `create`, `update`, `delete`
          * `invoice_instruction` - ad-hoc invoice instruction created by invoice rule
              * Actions: `create`, `update`, `delete`
          * `payment_instruction` - recurring or ad-hoc payment instruction represents how outgoing monies must be split to beneficiaries
              * Actions: `create`, `update`, `delete`
          * `credit_note` - instruction to decrease the amount of money a tenant owes
              * Actions: `create`, `delete`
          * `debit_note` - instruction to increase the amount of money a tenant owes
              * Actions: `create`, `delete`
          * `posted_payment` - unreconciled posted payment
              * Actions: `create`, `delete`
          * `property_account_balance` - changes in property account balance
              * Actions: `update`
          * `secondary_payment` - secondary payment that splits a portion of the primary payment amount to a different beneficiary
              * Actions: `create`, `update`, `delete`
          * `maintenance_ticket` - maintenance ticket
              * Actions: `create`, `update`
          * `maintenance_ticket_message` - maintenance ticket message
              * Actions: `create`
          * `outgoing_payment_batch` - outgoing payment batch notification
              * Actions: `update`
          * `expense_category` - expense category notification
              * Actions: `create`, `update`
          * `income_category` - income category notification
              * Actions: `create`, `update`
          * `damage_deposit` - damage deposit release notification
              * Actions: `create`
      * `data` - event metadata as key and value pairs containing various information about the event

    ### 9.4 Webhook event example

    The example below contains a single request sent to the webhook callback URL containing two events, indicating the beneficiary entity was updated and a new payment was reconciled.

    Please be advised that the event data will vary depending on the type of webhook emitted.

        {
            "agency": {
                "id": "zZyQ8NajZd",
                "name": "PayProp"
            },
            "events": [
                {
                    "action": "update",
                    "type": "beneficiary",
                    "data": {
                        "id": "G1OByGaaXM"
                    }
                },
                {
                    "action": "create",
                    "type": "payment",
                    "data": {
                        "id": "zZyQ8NajZd",
                        "status": "new",
                        "amount": "100.00",
                        "date": "2021-09-16",
                        "property_id": "D6JmYArJvG",
                        "tenant_id": "5AJ5DqjRZM"
                    }
                }
            ]
        }

    The example below illustrates content sent to the webhook callback URL on payment entity update. The new payment entity id can be found in the `updated_to_id` field.

        {
            "agency": {
                "id": "zZyQ8NajZd",
                "name": "PayProp"
            },
            "events": [
                {
                    "type": "payment_instruction",
                    "action": "update",
                    "data": {
                        "id": "2JkQEbGp1b",
                        "date": "2022-02-18",
                        "amount": "100.00",
                        "category_id": "6EyJ6RJjbv",
                        "updated_to_id": "RZQ73m89Xm",
                        "frequency": "O",
                        "beneficiary_type": "beneficiary",
                        "property_id": "mLZdvMl4Xn",
                        "beneficiary_id": "8b1g3GRW1G",
                        "percentage": "0.00"
                    }
                }
            ]
        }
  title: PayProp Agency API
  version: v1.1
  x-logo:
    url: /res/assets/img/pp_logo.svg
paths:
  /agents:
    get:
      description: "Return agents\n"
      operationId: get_agency_agents
      responses:
        '200':
          description: Agents.
          schema:
            $ref: api_definitions.yaml#/definitions/Agents
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Agents
      tags:
      - Agents
  /attachments/{entity}/{external_id}:
    get:
      description: "List file attachments\n"
      operationId: get_attachment_list
      parameters:
      - description: Restrict rows returned.
        in: query
        minimum: 1
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        minimum: 1
        name: page
        required: false
        type: integer
      - description: Entity type.
        enum:
        - tenant
        - property
        - beneficiary
        - payment
        - invoice-rule
        - invoice-instruction
        - maintenance-ticket
        - maintenance-ticket-image
        - maintenance-ticket-message-image
        in: path
        name: entity
        required: true
        type: string
      - description: External ID of entity.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Attachment list.
          schema:
            $ref: api_definitions.yaml#/definitions/AttachmentList
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: List file attachments
      tags:
      - Attachments
    post:
      consumes:
      - multipart/form-data
      description: |
        The upload content `Content-Disposition` header must be set to
        `Content-Disposition: form-data; name="attachment"; filename="YOUR_FILE_NAME"`.
      operationId: create_attachment
      parameters:
      - description: Entity type.
        enum:
        - tenant
        - property
        - beneficiary
        - payment
        - invoice-rule
        - invoice-instruction
        - maintenance-ticket
        - maintenance-ticket-image
        - maintenance-ticket-message-image
        in: path
        name: entity
        required: true
        type: string
      - description: External ID of entity.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: File to be attached
        in: formData
        name: attachment
        type: file
      responses:
        '200':
          description: File attached
          schema:
            $ref: api_definitions.yaml#/definitions/AttachmentListItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '409':
          description: Too many files for given entity.
          schema:
            $ref: api_definitions.yaml#/definitions/Error409
        '413':
          description: File too large.
          schema:
            $ref: api_definitions.yaml#/definitions/Error413
        '415':
          description: Invalid file type.
          schema:
            $ref: api_definitions.yaml#/definitions/Error415
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Upload file attachments
      tags:
      - Attachments
  /attachments/{external_id}:
    get:
      description: "Download file attachment\n"
      operationId: download_attachment_file
      parameters:
      - description: External ID of attachment.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Attachment file.
          schema:
            format: binary
            type: string
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Download file attachment
      tags:
      - Attachments
  /documents/pdf/agency-invoice:
    get:
      description: "Return agency invoice PDF\n"
      operationId: get_agency_invoice_pdf
      parameters:
      - description: Generate agency invoice for the given year.
        in: query
        minimum: 2000
        name: year
        required: true
        type: integer
        x-example: '2023'
      - description: Generate agency invoice for the given month.
        in: query
        maximum: 12
        minimum: 1
        name: month
        required: true
        type: integer
        x-example: '6'
      responses:
        '200':
          description: Agency invoice PDF.
          schema:
            format: binary
            type: string
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Agency invoice PDF
      tags:
      - Documents
  /documents/pdf/owner-statement:
    get:
      description: "Return owner statement PDF\n"
      operationId: get_owner_statement_pdf
      parameters:
      - description: External ID of property.
        in: query
        maxLength: 32
        name: property_id
        required: true
        type: string
      - description: External ID of beneficiary.
        in: query
        maxLength: 32
        name: beneficiary_id
        required: true
        type: string
      - description: Show report from given date (e.g. 2020-01-01)
        in: query
        name: from_date
        pattern: \d{4}-\d{2}-\d{2}
        required: false
        type: string
      - description: Show report to given date (e.g. 2020-01-31)
        in: query
        name: to_date
        pattern: \d{4}-\d{2}-\d{2}
        required: false
        type: string
      responses:
        '200':
          description: Owner statement PDF.
          schema:
            format: binary
            type: string
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Owner statement PDF
      tags:
      - Documents
  /entity/adhoc-invoice:
    post:
      consumes:
      - application/json
      description: "Create new adhoc invoice\n"
      operationId: create_adhoc_invoice
      parameters:
      - description: Adhoc invoice to create.
        in: body
        name: invoice
        schema:
          $schema: http://json-schema.org/draft-07/schema#
          additionalProperties: 0
          properties:
            amount:
              minimum: 0.01
              multipleOf: 0.01
              type: number
            category_id:
              description: 'Invoice category id. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency?version=v1.1#operation/get_invoice_categories).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            description:
              maxLength: 255
              type:
              - string
              - 'null'
            frequency:
              enum:
              - O
              type: string
            has_tax:
              type: boolean
            is_direct_debit:
              type: boolean
            property_id: &1
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            start_date:
              description: Default value of today if not provided.
              example: '2021-08-24'
              format: date
              type: string
            tax_amount:
              minimum: 0
              type:
              - number
              - string
            tenant_id: *1
          required:
          - tenant_id
          - property_id
          - amount
          - category_id
          type: object
      responses:
        '200':
          description: Created adhoc invoice.
          schema:
            $schema: http://json-schema.org/draft-07/schema#
            additionalProperties: 0
            properties:
              amount:
                minimum: 0.01
                multipleOf: 0.01
                type: number
              category_id:
                description: 'Invoice category id. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency?version=v1.1#operation/get_invoice_categories).'
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              deposit_id:
                description: Tenant payment reference
                type: string
              description:
                maxLength: 255
                type:
                - string
                - 'null'
              frequency:
                enum:
                - O
                type: string
              has_tax:
                type: boolean
              id: &2
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              is_direct_debit:
                type: boolean
              property_id: *2
              start_date:
                description: Default value of today if not provided.
                example: '2021-08-24'
                format: date
                type: string
              tax_amount:
                minimum: 0
                type:
                - number
                - string
              tenant_id: *2
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create adhoc invoice
      tags:
      - Adhoc Entity
  /entity/adhoc-payment:
    post:
      consumes:
      - application/json
      description: "Create new adhoc payment\n"
      operationId: create_adhoc_payment
      parameters:
      - description: Adhoc payment to create.
        in: body
        name: payment
        schema:
          $schema: http://json-schema.org/draft-07/schema#
          additionalProperties: 0
          properties:
            amount:
              minimum: 0.01
              multipleOf: 0.01
              type: number
            beneficiary_id: &3
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            beneficiary_type:
              enum:
              - agency
              - beneficiary
              - global_beneficiary
              - property_account
              - deposit_account
              type: string
            category_id:
              description: 'Payment category id. Ref: [/api/docs/agency#operation/get_payment_categories](/api/docs/agency?version=v1.1#operation/get_payment_categories).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            description:
              maxLength: 255
              type:
              - string
              - 'null'
            enabled:
              type: boolean
            frequency:
              enum:
              - O
              type: string
            global_beneficiary:
              description: This field is deprecated and will be removed in the future.
                Please make use of the `beneficiary_id` field.
              maxLength: 100
              minLength: 2
              type: string
            has_tax:
              type: boolean
            maintenance_ticket_id:
              description: 'Maintenance ticket external ID to which new payments must
                be associated with. Ref: [/api/docs/agency#tag/Maintenance](/api/docs/agency?version=v1.1#tag/Maintenance).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type:
              - 'null'
              - string
            no_commission_amount:
              minimum: 0.01
              type:
              - number
              - 'null'
            property_id: *3
            reference:
              description: Required if `beneficiary_type` is **beneficiary**.
              maxLength: 50
              type: string
            start_date:
              description: Default value of today if not provided.
              example: '2021-08-24'
              format: date
              type: string
            tax_amount:
              minimum: 0
              type:
              - number
              - string
            tenant_id: *3
            use_money_from:
              enum:
              - any_tenant
              - tenant
              - property_account
              type: string
          required:
          - property_id
          - beneficiary_type
          - frequency
          - amount
          type: object
      responses:
        '200':
          description: Created adhoc payment.
          schema:
            $schema: http://json-schema.org/draft-07/schema#
            additionalProperties: 0
            properties:
              amount:
                minimum: 0.01
                multipleOf: 0.01
                type: number
              beneficiary_id: &4
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              beneficiary_type:
                enum:
                - agency
                - beneficiary
                - global_beneficiary
                - property_account
                - deposit_account
                type: string
              category_id:
                description: 'Payment category id. Ref: [/api/docs/agency#operation/get_payment_categories](/api/docs/agency?version=v1.1#operation/get_payment_categories).'
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              description:
                maxLength: 255
                type:
                - string
                - 'null'
              enabled:
                type: boolean
              frequency:
                enum:
                - O
                type: string
              global_beneficiary:
                description: This field is deprecated and will be removed in the future.
                  Please make use of the `beneficiary_id` field.
                maxLength: 100
                minLength: 2
                type: string
              has_tax:
                type: boolean
              id: *4
              maintenance_ticket_id:
                description: 'Maintenance ticket external ID to which new payments
                  must be associated with. Ref: [/api/docs/agency#tag/Maintenance](/api/docs/agency?version=v1.1#tag/Maintenance).'
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type:
                - 'null'
                - string
              no_commission_amount:
                minimum: 0.01
                type:
                - number
                - 'null'
              property_id: *4
              reference:
                description: Required if `beneficiary_type` is **beneficiary**.
                maxLength: 50
                type: string
              start_date:
                description: Default value of today if not provided.
                example: '2021-08-24'
                format: date
                type: string
              tax_amount:
                minimum: 0
                type:
                - number
                - string
              tenant_id: *4
              use_money_from:
                enum:
                - any_tenant
                - tenant
                - property_account
                type: string
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create adhoc payment
      tags:
      - Adhoc Entity
  /entity/beneficiary:
    post:
      consumes:
      - application/json
      description: "Create new beneficiary\n"
      operationId: create_beneficiary
      parameters:
      - description: Beneficiary to create.
        in: body
        name: beneficiary
        schema:
          $schema: http://json-schema.org/schema#
          additionalProperties: 0
          allOf:
          - if:
              properties:
                account_type:
                  const: individual
            then:
              properties:
                first_name:
                  minLength: 1
                last_name:
                  minLength: 1
              required:
              - first_name
              - last_name
          - if:
              properties:
                account_type:
                  const: business
            then:
              properties:
                business_name:
                  minLength: 1
              required:
              - business_name
          - if:
              properties:
                payment_method:
                  const: international
            then:
              properties:
                address:
                  properties:
                    address_line_1:
                      minLength: 1
                    city:
                      minLength: 1
                    postal_code:
                      minLength: 1
                    state:
                      minLength: 1
                  required:
                  - address_line_1
                  - city
                  - state
                  - postal_code
                bank_account:
                  oneOf:
                  - required:
                    - iban
                  - required:
                    - account_number
                  properties:
                    account_number:
                      pattern: ^[a-zA-Z0-9\- ]+$
                  required:
                  - swift_code
                  - country_code
                  - account_name
          - if:
              properties:
                payment_method:
                  const: cheque
            then:
              properties:
                address:
                  properties:
                    address_line_1:
                      minLength: 1
                    city:
                      minLength: 1
                    postal_code:
                      minLength: 1
                    state:
                      minLength: 1
                  required:
                  - address_line_1
                  - city
                  - state
                  - postal_code
          - if:
              properties:
                payment_method:
                  const: local
            then:
              properties:
                bank_account:
                  properties:
                    account_number:
                      maxLength: 8
                      minLength: 3
                      pattern: ^\d+$
                    branch_code:
                      maxLength: 6
                      minLength: 6
                  required:
                  - account_name
                  - account_number
                  - branch_code
          - if:
              properties:
                mobile:
                  minLength: 1
            then:
              properties:
                mobile:
                  pattern: ^[1-9]\d+$
          properties:
            account_type:
              default: individual
              description: This determines how the beneficiary is addressed on their
                communications.<br><br>Individuals are addressed by the `first_name`
                and `last_name` field, whereas a business is addressed by the `business_name`
                field.
              enum:
              - individual
              - business
              type: string
            address:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The address that will be used on tenant communications.
                Typically this is the rental address, but can be a guarantor.
              properties:
                address_line_1:
                  description: Required for international beneficiary
                  maxLength: 50
                  type: string
                address_line_2:
                  maxLength: 50
                  type: string
                address_line_3:
                  maxLength: 50
                  type: string
                city:
                  description: Required for international beneficiary
                  maxLength: 50
                  type: string
                country_code:
                  default: UK
                  enum: &5
                  - AD
                  - AE
                  - AF
                  - AG
                  - AI
                  - AL
                  - AM
                  - AN
                  - AO
                  - AQ
                  - AR
                  - AS
                  - AT
                  - AU
                  - AW
                  - AX
                  - AZ
                  - BA
                  - BB
                  - BD
                  - BE
                  - BF
                  - BG
                  - BH
                  - BI
                  - BJ
                  - BL
                  - BM
                  - BN
                  - BO
                  - BQ
                  - BR
                  - BS
                  - BT
                  - BV
                  - BW
                  - BY
                  - BZ
                  - CA
                  - CC
                  - CD
                  - CF
                  - CG
                  - CH
                  - CI
                  - CK
                  - CL
                  - CM
                  - CN
                  - CO
                  - CR
                  - CU
                  - CV
                  - CW
                  - CX
                  - CY
                  - CZ
                  - DE
                  - DJ
                  - DK
                  - DM
                  - DO
                  - DZ
                  - EC
                  - EE
                  - EG
                  - EH
                  - ER
                  - ES
                  - ET
                  - FI
                  - FJ
                  - FK
                  - FM
                  - FO
                  - FR
                  - GA
                  - GD
                  - GE
                  - GF
                  - GG
                  - GH
                  - GI
                  - GL
                  - GM
                  - GN
                  - GP
                  - GQ
                  - GR
                  - GS
                  - GT
                  - GU
                  - GW
                  - GY
                  - HK
                  - HM
                  - HN
                  - HR
                  - HT
                  - HU
                  - ID
                  - IE
                  - IL
                  - IM
                  - IN
                  - IO
                  - IQ
                  - IR
                  - IS
                  - IT
                  - JE
                  - JM
                  - JO
                  - JP
                  - KE
                  - KG
                  - KH
                  - KI
                  - KM
                  - KN
                  - KP
                  - KR
                  - KW
                  - KY
                  - KZ
                  - LA
                  - LB
                  - LC
                  - LI
                  - LK
                  - LR
                  - LS
                  - LT
                  - LU
                  - LV
                  - LY
                  - MA
                  - MC
                  - MD
                  - ME
                  - MF
                  - MG
                  - MH
                  - MK
                  - ML
                  - MM
                  - MN
                  - MO
                  - MP
                  - MQ
                  - MR
                  - MS
                  - MT
                  - MU
                  - MV
                  - MW
                  - MX
                  - MY
                  - MZ
                  - NA
                  - NC
                  - NE
                  - NF
                  - NG
                  - NI
                  - NL
                  - NO
                  - NP
                  - NR
                  - NU
                  - NZ
                  - OM
                  - PA
                  - PE
                  - PF
                  - PG
                  - PH
                  - PK
                  - PL
                  - PM
                  - PN
                  - PR
                  - PS
                  - PT
                  - PW
                  - PY
                  - QA
                  - RE
                  - RO
                  - RS
                  - RU
                  - RW
                  - SA
                  - SB
                  - SC
                  - SD
                  - SE
                  - SG
                  - SH
                  - SI
                  - SJ
                  - SK
                  - SL
                  - SM
                  - SN
                  - SO
                  - SR
                  - SS
                  - ST
                  - SV
                  - SX
                  - SY
                  - SZ
                  - TC
                  - TD
                  - TF
                  - TG
                  - TH
                  - TJ
                  - TK
                  - TL
                  - TM
                  - TN
                  - TO
                  - TP
                  - TR
                  - TT
                  - TV
                  - TW
                  - TZ
                  - UA
                  - UG
                  - UK
                  - UM
                  - US
                  - UY
                  - UZ
                  - VA
                  - VC
                  - VE
                  - VG
                  - VI
                  - VN
                  - VU
                  - WF
                  - WS
                  - YE
                  - YT
                  - YU
                  - ZA
                  - ZM
                  - ZR
                  - ZW
                  type: string
                latitude:
                  maxLength: 50
                  type: string
                longitude:
                  maxLength: 50
                  type: string
                postal_code:
                  description: Required for international beneficiary
                  maxLength: 10
                  type: string
                state:
                  description: Required for international beneficiary
                  maxLength: 50
                  type: string
              type: object
            bank_account:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              properties:
                account_name:
                  maxLength: 50
                  minLength: 1
                  type: string
                account_number:
                  description: Supported for international and non-international beneficiaries.
                    Please provide either iban or account_number, not both.
                  type: string
                bank_name:
                  maxLength: 50
                  type: string
                branch_code:
                  description: Required for non-international beneficiaries
                  pattern: ^\d+$
                  type: string
                branch_name:
                  maxLength: 50
                  type: string
                country_code:
                  default: UK
                  description: Required for international beneficiaries
                  enum: *5
                  type: string
                iban:
                  description: Supported for international beneficiaries. Please provide
                    either iban or account_number, not both.
                  maxLength: 34
                  pattern: ^[A-Za-z0-9]+$
                  type: string
                swift_code:
                  description: Required for international beneficiaries
                  maxLength: 11
                  minLength: 8
                  type: string
              type: object
            business_name:
              description: Required if account_type is business
              maxLength: 50
              type: string
            comment:
              maxLength: 6000
              type: string
            communication_preferences:
              description: This determines how the beneficiary prefers to receive
                their communications.
              properties:
                email:
                  properties:
                    enabled:
                      description: 'Specify whether you want the beneficiary to receive
                        email notifications. Default: `true`<br>'
                      type: boolean
                    payment_advice:
                      description: 'Default: `true`'
                      type: boolean
                  type: object
              type: object
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            customer_reference:
              description: A unique value you can add to identify the beneficiary.
                This is visible on the platform.
              maxLength: 50
              type: string
            email_address:
              description: Specify the email address that you would like the beneficiaries
                communications to go to.<br><br>This also gives access to the owner
                app for landlords.<br><br>This will include remittance advices which
                are generated when payments are made to the beneficiary, or in the
                case of landlords when funds are processed through their property.<br/><br>Additionally,
                landlords will receive a monthly owner statement if the agency has
                this feature activated.
              format: email
              maxLength: 100
              type:
              - string
              - 'null'
            email_cc:
              default: []
              description: Up to 10 email addresses can be listed here, recipients
                will be copied on the owners communications but landlords will not
                be given access to the owner app.
              items:
                format: email
                type: string
              maxItems: 10
              type: array
            fax:
              maxLength: 15
              type: string
            first_name:
              description: Required if account_type is individual
              maxLength: 100
              type: string
            id_number:
              description: Personal identification number from e.g. Passport/driving
                license
              maxLength: 50
              type: string
            id_type_id:
              default: ~
              description: 'ID Type external id. Ref: [/api/docs/agency?version=v1.1#operation/get_entity_id_types](/api/docs/agency?version=v1.1#operation/get_entity_id_types).'
              maxLength: 32
              type:
              - string
              - 'null'
            international:
              description: This field is deprecated and will be removed in future,
                please use **payment_method** field when creating or updating entities.
              type: boolean
            last_name:
              description: Required if account_type is individual
              maxLength: 100
              type: string
            mobile:
              description: The mobile phone number you would like receive notifications
                regarding this beneficiary.<br><br>This will include short versions
                of our remittance advices which are generated when payments are made
                to the beneficiary, or in the case of landlords when funds are processed
                through their property.<br><br>Owner statements are not sent via SMS.
                Please note you must include the country code.
              maxLength: 25
              type:
              - string
              - 'null'
            notify_email:
              description: "Default: `true`<br>\n\t\t\t\t\tSpecify whether you want
                the beneficiary to receive email notifications.\n\t\t\t\t\t<br><br>\n\t\t\t\t\tThis
                field is deprecated and will be removed in future, please use `communication_preferences`
                when creating or updating entities.\n\t\t\t\t"
              type: boolean
            notify_sms:
              description: "Default: `true`<br>\n\t\t\t\t\tSpecify whether you want
                the beneficiary to receive sms notifications.\n\t\t\t\t\t<br><br>\n\t\t\t\t\tThis
                field is deprecated and will be removed in future, please use `communication_preferences`
                when creating or updating entities.\n\t\t\t\t"
              type: boolean
            payment_method:
              default: local
              description: "<code>international</code> The beneficiary will use an
                IBAN and SWIFT code.<br>\n\t\t\t\t<code>local</code> The beneficiary
                will use bank details formatted for your country.<br>\n\t\t\t\t<code>cheque</code>
                (US Only) The beneficiary will be paid by a cheque cut by the bank."
              enum:
              - local
              - international
              - cheque
              type: string
            phone:
              description: An optional field that is only used for record keeping,
                notifications will not be sent here.
              maxLength: 15
              type: string
            vat_number:
              maxLength: 50
              type: string
          required: []
          type: object
      responses:
        '200':
          description: Created beneficiary.
          schema: &6
            $schema: http://json-schema.org/schema#
            additionalProperties: 0
            allOf:
            - if:
                properties:
                  account_type:
                    const: individual
              then:
                properties:
                  first_name:
                    minLength: 1
                  last_name:
                    minLength: 1
            - if:
                properties:
                  account_type:
                    const: business
              then:
                properties:
                  business_name:
                    minLength: 1
            - if:
                properties:
                  payment_method:
                    const: international
              then:
                properties:
                  address:
                    properties:
                      address_line_1:
                        minLength: 1
                      city:
                        minLength: 1
                      postal_code:
                        minLength: 1
                      state:
                        minLength: 1
                  bank_account:
                    oneOf:
                    - {}
                    - {}
                    properties:
                      account_number:
                        pattern: ^[a-zA-Z0-9\- ]+$
            - if:
                properties:
                  payment_method:
                    const: cheque
              then:
                properties:
                  address:
                    properties:
                      address_line_1:
                        minLength: 1
                      city:
                        minLength: 1
                      postal_code:
                        minLength: 1
                      state:
                        minLength: 1
            - if:
                properties:
                  payment_method:
                    const: local
              then:
                properties:
                  bank_account:
                    properties:
                      account_number:
                        maxLength: 8
                        minLength: 3
                        pattern: ^\d+$
                      branch_code:
                        maxLength: 6
                        minLength: 6
            - if:
                properties:
                  mobile:
                    minLength: 1
              then:
                properties:
                  mobile:
                    pattern: ^[1-9]\d+$
            properties:
              account_type:
                description: This determines how the beneficiary is addressed on their
                  communications.<br><br>Individuals are addressed by the `first_name`
                  and `last_name` field, whereas a business is addressed by the `business_name`
                  field.
                enum:
                - individual
                - business
                type: string
              address:
                $schema: http://json-schema.org/schema#
                additionalProperties: 0
                description: The address that will be used on tenant communications.
                  Typically this is the rental address, but can be a guarantor.
                properties:
                  address_line_1:
                    description: Required for international beneficiary
                    maxLength: 50
                    type: string
                  address_line_2:
                    maxLength: 50
                    type: string
                  address_line_3:
                    maxLength: 50
                    type: string
                  city:
                    description: Required for international beneficiary
                    maxLength: 50
                    type: string
                  country_code:
                    enum: &7
                    - AD
                    - AE
                    - AF
                    - AG
                    - AI
                    - AL
                    - AM
                    - AN
                    - AO
                    - AQ
                    - AR
                    - AS
                    - AT
                    - AU
                    - AW
                    - AX
                    - AZ
                    - BA
                    - BB
                    - BD
                    - BE
                    - BF
                    - BG
                    - BH
                    - BI
                    - BJ
                    - BL
                    - BM
                    - BN
                    - BO
                    - BQ
                    - BR
                    - BS
                    - BT
                    - BV
                    - BW
                    - BY
                    - BZ
                    - CA
                    - CC
                    - CD
                    - CF
                    - CG
                    - CH
                    - CI
                    - CK
                    - CL
                    - CM
                    - CN
                    - CO
                    - CR
                    - CU
                    - CV
                    - CW
                    - CX
                    - CY
                    - CZ
                    - DE
                    - DJ
                    - DK
                    - DM
                    - DO
                    - DZ
                    - EC
                    - EE
                    - EG
                    - EH
                    - ER
                    - ES
                    - ET
                    - FI
                    - FJ
                    - FK
                    - FM
                    - FO
                    - FR
                    - GA
                    - GD
                    - GE
                    - GF
                    - GG
                    - GH
                    - GI
                    - GL
                    - GM
                    - GN
                    - GP
                    - GQ
                    - GR
                    - GS
                    - GT
                    - GU
                    - GW
                    - GY
                    - HK
                    - HM
                    - HN
                    - HR
                    - HT
                    - HU
                    - ID
                    - IE
                    - IL
                    - IM
                    - IN
                    - IO
                    - IQ
                    - IR
                    - IS
                    - IT
                    - JE
                    - JM
                    - JO
                    - JP
                    - KE
                    - KG
                    - KH
                    - KI
                    - KM
                    - KN
                    - KP
                    - KR
                    - KW
                    - KY
                    - KZ
                    - LA
                    - LB
                    - LC
                    - LI
                    - LK
                    - LR
                    - LS
                    - LT
                    - LU
                    - LV
                    - LY
                    - MA
                    - MC
                    - MD
                    - ME
                    - MF
                    - MG
                    - MH
                    - MK
                    - ML
                    - MM
                    - MN
                    - MO
                    - MP
                    - MQ
                    - MR
                    - MS
                    - MT
                    - MU
                    - MV
                    - MW
                    - MX
                    - MY
                    - MZ
                    - NA
                    - NC
                    - NE
                    - NF
                    - NG
                    - NI
                    - NL
                    - NO
                    - NP
                    - NR
                    - NU
                    - NZ
                    - OM
                    - PA
                    - PE
                    - PF
                    - PG
                    - PH
                    - PK
                    - PL
                    - PM
                    - PN
                    - PR
                    - PS
                    - PT
                    - PW
                    - PY
                    - QA
                    - RE
                    - RO
                    - RS
                    - RU
                    - RW
                    - SA
                    - SB
                    - SC
                    - SD
                    - SE
                    - SG
                    - SH
                    - SI
                    - SJ
                    - SK
                    - SL
                    - SM
                    - SN
                    - SO
                    - SR
                    - SS
                    - ST
                    - SV
                    - SX
                    - SY
                    - SZ
                    - TC
                    - TD
                    - TF
                    - TG
                    - TH
                    - TJ
                    - TK
                    - TL
                    - TM
                    - TN
                    - TO
                    - TP
                    - TR
                    - TT
                    - TV
                    - TW
                    - TZ
                    - UA
                    - UG
                    - UK
                    - UM
                    - US
                    - UY
                    - UZ
                    - VA
                    - VC
                    - VE
                    - VG
                    - VI
                    - VN
                    - VU
                    - WF
                    - WS
                    - YE
                    - YT
                    - YU
                    - ZA
                    - ZM
                    - ZR
                    - ZW
                    type: string
                  latitude:
                    maxLength: 50
                    type: string
                  longitude:
                    maxLength: 50
                    type: string
                  postal_code:
                    description: Required for international beneficiary
                    maxLength: 10
                    type: string
                  state:
                    description: Required for international beneficiary
                    maxLength: 50
                    type: string
                type: object
              bank_account:
                $schema: http://json-schema.org/schema#
                additionalProperties: 0
                properties:
                  account_name:
                    maxLength: 50
                    minLength: 1
                    type: string
                  account_number:
                    description: Supported for international and non-international
                      beneficiaries. Please provide either iban or account_number,
                      not both.
                    type: string
                  bank_name:
                    maxLength: 50
                    type: string
                  branch_code:
                    description: Required for non-international beneficiaries
                    pattern: ^\d+$
                    type: string
                  branch_name:
                    maxLength: 50
                    type: string
                  country_code:
                    description: Required for international beneficiaries
                    enum: *7
                    type: string
                  iban:
                    description: Supported for international beneficiaries. Please
                      provide either iban or account_number, not both.
                    maxLength: 34
                    pattern: ^[A-Za-z0-9]+$
                    type: string
                  swift_code:
                    description: Required for international beneficiaries
                    maxLength: 11
                    minLength: 8
                    type: string
                type: object
              business_name:
                description: Required if account_type is business
                maxLength: 50
                type: string
              comment:
                maxLength: 6000
                type: string
              communication_preferences:
                description: This determines how the beneficiary prefers to receive
                  their communications.
                properties:
                  email:
                    properties:
                      enabled:
                        description: 'Specify whether you want the beneficiary to
                          receive email notifications. Default: `true`<br>'
                        type: boolean
                      payment_advice:
                        description: 'Default: `true`'
                        type: boolean
                    type: object
                type: object
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              customer_reference:
                description: A unique value you can add to identify the beneficiary.
                  This is visible on the platform.
                maxLength: 50
                type: string
              email_address:
                description: Specify the email address that you would like the beneficiaries
                  communications to go to.<br><br>This also gives access to the owner
                  app for landlords.<br><br>This will include remittance advices which
                  are generated when payments are made to the beneficiary, or in the
                  case of landlords when funds are processed through their property.<br/><br>Additionally,
                  landlords will receive a monthly owner statement if the agency has
                  this feature activated.
                format: email
                maxLength: 100
                type:
                - string
                - 'null'
              email_cc:
                description: Up to 10 email addresses can be listed here, recipients
                  will be copied on the owners communications but landlords will not
                  be given access to the owner app.
                items:
                  format: email
                  type: string
                maxItems: 10
                type: array
              fax:
                maxLength: 15
                type: string
              first_name:
                description: Required if account_type is individual
                maxLength: 100
                type: string
              id:
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              id_number:
                description: Personal identification number from e.g. Passport/driving
                  license
                maxLength: 50
                type: string
              id_type_id:
                description: 'ID Type external id. Ref: [/api/docs/agency?version=v1.1#operation/get_entity_id_types](/api/docs/agency?version=v1.1#operation/get_entity_id_types).'
                maxLength: 32
                type:
                - string
                - 'null'
              international:
                description: This field is deprecated and will be removed in future,
                  please use **payment_method** field when creating or updating entities.
                type: boolean
              last_name:
                description: Required if account_type is individual
                maxLength: 100
                type: string
              mobile:
                description: The mobile phone number you would like receive notifications
                  regarding this beneficiary.<br><br>This will include short versions
                  of our remittance advices which are generated when payments are
                  made to the beneficiary, or in the case of landlords when funds
                  are processed through their property.<br><br>Owner statements are
                  not sent via SMS. Please note you must include the country code.
                maxLength: 25
                type:
                - string
                - 'null'
              notify_email:
                description: "Default: `true`<br>\n\t\t\t\t\tSpecify whether you want
                  the beneficiary to receive email notifications.\n\t\t\t\t\t<br><br>\n\t\t\t\t\tThis
                  field is deprecated and will be removed in future, please use `communication_preferences`
                  when creating or updating entities.\n\t\t\t\t"
                type: boolean
              notify_sms:
                description: "Default: `true`<br>\n\t\t\t\t\tSpecify whether you want
                  the beneficiary to receive sms notifications.\n\t\t\t\t\t<br><br>\n\t\t\t\t\tThis
                  field is deprecated and will be removed in future, please use `communication_preferences`
                  when creating or updating entities.\n\t\t\t\t"
                type: boolean
              payment_method:
                description: "<code>international</code> The beneficiary will use
                  an IBAN and SWIFT code.<br>\n\t\t\t\t<code>local</code> The beneficiary
                  will use bank details formatted for your country.<br>\n\t\t\t\t<code>cheque</code>
                  (US Only) The beneficiary will be paid by a cheque cut by the bank."
                enum:
                - local
                - international
                - cheque
                type: string
              phone:
                description: An optional field that is only used for record keeping,
                  notifications will not be sent here.
                maxLength: 15
                type: string
              vat_number:
                maxLength: 50
                type: string
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create beneficiary
      tags:
      - Beneficiary Entity
  /entity/beneficiary/{external_id}:
    get:
      description: "Get beneficiary\n"
      operationId: get_beneficiary
      parameters:
      - description: External ID of beneficiary.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Retrieved beneficiary.
          schema: *6
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get beneficiary
      tags:
      - Beneficiary Entity
    put:
      consumes:
      - application/json
      description: "Update beneficiary\n"
      operationId: update_beneficiary
      parameters:
      - description: Beneficiary to update.
        in: body
        name: beneficiary
        schema:
          $schema: http://json-schema.org/schema#
          additionalProperties: 0
          allOf:
          - if:
              properties:
                account_type:
                  const: individual
            then:
              properties:
                first_name:
                  minLength: 1
                last_name:
                  minLength: 1
          - if:
              properties:
                account_type:
                  const: business
            then:
              properties:
                business_name:
                  minLength: 1
          - if:
              properties:
                payment_method:
                  const: international
            then:
              properties:
                address:
                  properties:
                    address_line_1:
                      minLength: 1
                    city:
                      minLength: 1
                    postal_code:
                      minLength: 1
                    state:
                      minLength: 1
                bank_account:
                  oneOf:
                  - {}
                  - {}
                  properties:
                    account_number:
                      pattern: ^[a-zA-Z0-9\- ]+$
          - if:
              properties:
                payment_method:
                  const: cheque
            then:
              properties:
                address:
                  properties:
                    address_line_1:
                      minLength: 1
                    city:
                      minLength: 1
                    postal_code:
                      minLength: 1
                    state:
                      minLength: 1
          - if:
              properties:
                payment_method:
                  const: local
            then:
              properties:
                bank_account:
                  properties:
                    account_number:
                      maxLength: 8
                      minLength: 3
                      pattern: ^\d+$
                    branch_code:
                      maxLength: 6
                      minLength: 6
          - if:
              properties:
                mobile:
                  minLength: 1
            then:
              properties:
                mobile:
                  pattern: ^[1-9]\d+$
          properties:
            account_type:
              description: This determines how the beneficiary is addressed on their
                communications.<br><br>Individuals are addressed by the `first_name`
                and `last_name` field, whereas a business is addressed by the `business_name`
                field.
              enum:
              - individual
              - business
              type: string
            address:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The address that will be used on tenant communications.
                Typically this is the rental address, but can be a guarantor.
              properties:
                address_line_1:
                  description: Required for international beneficiary
                  maxLength: 50
                  type: string
                address_line_2:
                  maxLength: 50
                  type: string
                address_line_3:
                  maxLength: 50
                  type: string
                city:
                  description: Required for international beneficiary
                  maxLength: 50
                  type: string
                country_code:
                  enum: &8
                  - AD
                  - AE
                  - AF
                  - AG
                  - AI
                  - AL
                  - AM
                  - AN
                  - AO
                  - AQ
                  - AR
                  - AS
                  - AT
                  - AU
                  - AW
                  - AX
                  - AZ
                  - BA
                  - BB
                  - BD
                  - BE
                  - BF
                  - BG
                  - BH
                  - BI
                  - BJ
                  - BL
                  - BM
                  - BN
                  - BO
                  - BQ
                  - BR
                  - BS
                  - BT
                  - BV
                  - BW
                  - BY
                  - BZ
                  - CA
                  - CC
                  - CD
                  - CF
                  - CG
                  - CH
                  - CI
                  - CK
                  - CL
                  - CM
                  - CN
                  - CO
                  - CR
                  - CU
                  - CV
                  - CW
                  - CX
                  - CY
                  - CZ
                  - DE
                  - DJ
                  - DK
                  - DM
                  - DO
                  - DZ
                  - EC
                  - EE
                  - EG
                  - EH
                  - ER
                  - ES
                  - ET
                  - FI
                  - FJ
                  - FK
                  - FM
                  - FO
                  - FR
                  - GA
                  - GD
                  - GE
                  - GF
                  - GG
                  - GH
                  - GI
                  - GL
                  - GM
                  - GN
                  - GP
                  - GQ
                  - GR
                  - GS
                  - GT
                  - GU
                  - GW
                  - GY
                  - HK
                  - HM
                  - HN
                  - HR
                  - HT
                  - HU
                  - ID
                  - IE
                  - IL
                  - IM
                  - IN
                  - IO
                  - IQ
                  - IR
                  - IS
                  - IT
                  - JE
                  - JM
                  - JO
                  - JP
                  - KE
                  - KG
                  - KH
                  - KI
                  - KM
                  - KN
                  - KP
                  - KR
                  - KW
                  - KY
                  - KZ
                  - LA
                  - LB
                  - LC
                  - LI
                  - LK
                  - LR
                  - LS
                  - LT
                  - LU
                  - LV
                  - LY
                  - MA
                  - MC
                  - MD
                  - ME
                  - MF
                  - MG
                  - MH
                  - MK
                  - ML
                  - MM
                  - MN
                  - MO
                  - MP
                  - MQ
                  - MR
                  - MS
                  - MT
                  - MU
                  - MV
                  - MW
                  - MX
                  - MY
                  - MZ
                  - NA
                  - NC
                  - NE
                  - NF
                  - NG
                  - NI
                  - NL
                  - NO
                  - NP
                  - NR
                  - NU
                  - NZ
                  - OM
                  - PA
                  - PE
                  - PF
                  - PG
                  - PH
                  - PK
                  - PL
                  - PM
                  - PN
                  - PR
                  - PS
                  - PT
                  - PW
                  - PY
                  - QA
                  - RE
                  - RO
                  - RS
                  - RU
                  - RW
                  - SA
                  - SB
                  - SC
                  - SD
                  - SE
                  - SG
                  - SH
                  - SI
                  - SJ
                  - SK
                  - SL
                  - SM
                  - SN
                  - SO
                  - SR
                  - SS
                  - ST
                  - SV
                  - SX
                  - SY
                  - SZ
                  - TC
                  - TD
                  - TF
                  - TG
                  - TH
                  - TJ
                  - TK
                  - TL
                  - TM
                  - TN
                  - TO
                  - TP
                  - TR
                  - TT
                  - TV
                  - TW
                  - TZ
                  - UA
                  - UG
                  - UK
                  - UM
                  - US
                  - UY
                  - UZ
                  - VA
                  - VC
                  - VE
                  - VG
                  - VI
                  - VN
                  - VU
                  - WF
                  - WS
                  - YE
                  - YT
                  - YU
                  - ZA
                  - ZM
                  - ZR
                  - ZW
                  type: string
                latitude:
                  maxLength: 50
                  type: string
                longitude:
                  maxLength: 50
                  type: string
                postal_code:
                  description: Required for international beneficiary
                  maxLength: 10
                  type: string
                state:
                  description: Required for international beneficiary
                  maxLength: 50
                  type: string
              type: object
            bank_account:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              properties:
                account_name:
                  maxLength: 50
                  minLength: 1
                  type: string
                account_number:
                  description: Supported for international and non-international beneficiaries.
                    Please provide either iban or account_number, not both.
                  type: string
                bank_name:
                  maxLength: 50
                  type: string
                branch_code:
                  description: Required for non-international beneficiaries
                  pattern: ^\d+$
                  type: string
                branch_name:
                  maxLength: 50
                  type: string
                country_code:
                  description: Required for international beneficiaries
                  enum: *8
                  type: string
                iban:
                  description: Supported for international beneficiaries. Please provide
                    either iban or account_number, not both.
                  maxLength: 34
                  pattern: ^[A-Za-z0-9]+$
                  type: string
                swift_code:
                  description: Required for international beneficiaries
                  maxLength: 11
                  minLength: 8
                  type: string
              type: object
            business_name:
              description: Required if account_type is business
              maxLength: 50
              type: string
            comment:
              maxLength: 6000
              type: string
            communication_preferences:
              description: This determines how the beneficiary prefers to receive
                their communications.
              properties:
                email:
                  properties:
                    enabled:
                      description: 'Specify whether you want the beneficiary to receive
                        email notifications. Default: `true`<br>'
                      type: boolean
                    payment_advice:
                      description: 'Default: `true`'
                      type: boolean
                  type: object
              type: object
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            customer_reference:
              description: A unique value you can add to identify the beneficiary.
                This is visible on the platform.
              maxLength: 50
              type: string
            email_address:
              description: Specify the email address that you would like the beneficiaries
                communications to go to.<br><br>This also gives access to the owner
                app for landlords.<br><br>This will include remittance advices which
                are generated when payments are made to the beneficiary, or in the
                case of landlords when funds are processed through their property.<br/><br>Additionally,
                landlords will receive a monthly owner statement if the agency has
                this feature activated.
              format: email
              maxLength: 100
              type:
              - string
              - 'null'
            email_cc:
              description: Up to 10 email addresses can be listed here, recipients
                will be copied on the owners communications but landlords will not
                be given access to the owner app.
              items:
                format: email
                type: string
              maxItems: 10
              type: array
            fax:
              maxLength: 15
              type: string
            first_name:
              description: Required if account_type is individual
              maxLength: 100
              type: string
            id_number:
              description: Personal identification number from e.g. Passport/driving
                license
              maxLength: 50
              type: string
            id_type_id:
              description: 'ID Type external id. Ref: [/api/docs/agency?version=v1.1#operation/get_entity_id_types](/api/docs/agency?version=v1.1#operation/get_entity_id_types).'
              maxLength: 32
              type:
              - string
              - 'null'
            international:
              description: This field is deprecated and will be removed in future,
                please use **payment_method** field when creating or updating entities.
              type: boolean
            last_name:
              description: Required if account_type is individual
              maxLength: 100
              type: string
            mobile:
              description: The mobile phone number you would like receive notifications
                regarding this beneficiary.<br><br>This will include short versions
                of our remittance advices which are generated when payments are made
                to the beneficiary, or in the case of landlords when funds are processed
                through their property.<br><br>Owner statements are not sent via SMS.
                Please note you must include the country code.
              maxLength: 25
              type:
              - string
              - 'null'
            notify_email:
              description: "Default: `true`<br>\n\t\t\t\t\tSpecify whether you want
                the beneficiary to receive email notifications.\n\t\t\t\t\t<br><br>\n\t\t\t\t\tThis
                field is deprecated and will be removed in future, please use `communication_preferences`
                when creating or updating entities.\n\t\t\t\t"
              type: boolean
            notify_sms:
              description: "Default: `true`<br>\n\t\t\t\t\tSpecify whether you want
                the beneficiary to receive sms notifications.\n\t\t\t\t\t<br><br>\n\t\t\t\t\tThis
                field is deprecated and will be removed in future, please use `communication_preferences`
                when creating or updating entities.\n\t\t\t\t"
              type: boolean
            payment_method:
              description: "<code>international</code> The beneficiary will use an
                IBAN and SWIFT code.<br>\n\t\t\t\t<code>local</code> The beneficiary
                will use bank details formatted for your country.<br>\n\t\t\t\t<code>cheque</code>
                (US Only) The beneficiary will be paid by a cheque cut by the bank."
              enum:
              - local
              - international
              - cheque
              type: string
            phone:
              description: An optional field that is only used for record keeping,
                notifications will not be sent here.
              maxLength: 15
              type: string
            vat_number:
              maxLength: 50
              type: string
          type: object
      - description: External ID of beneficiary.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Updated beneficiary.
          schema: *6
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update beneficiary
      tags:
      - Beneficiary Entity
  /entity/invoice:
    post:
      consumes:
      - application/json
      description: "Create new invoice\n"
      operationId: create_invoice
      parameters:
      - description: Invoice to create.
        in: body
        name: invoice
        schema:
          $schema: http://json-schema.org/draft-07/schema#
          additionalProperties: 0
          properties:
            amount:
              minimum: 0.01
              multipleOf: 0.01
              type: number
            category_id:
              description: 'Invoice category id. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency?version=v1.1#operation/get_invoice_categories).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            description:
              maxLength: 255
              type:
              - string
              - 'null'
            end_date:
              example: '2021-09-24'
              format: date
              type:
              - string
              - 'null'
            frequency:
              description: 'Ref: [/api/docs/agency?version=v1.1#operation/get_transaction_frequencies](/api/docs/agency#operation/get_transaction_frequencies).'
              enum:
              - O
              - W
              - 2W
              - 4W
              - M
              - 2M
              - Q
              - 6M
              - A
              type: string
            has_invoice_period:
              description: Available for reoccurring invoices
              type: boolean
            has_tax:
              type: boolean
            is_direct_debit:
              type: boolean
            payment_day:
              maximum: 31
              minimum: 1
              type: integer
            property_id: &9
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            start_date:
              description: Default value of today if not provided.
              example: '2021-08-24'
              format: date
              type: string
            tax_amount:
              minimum: 0
              type:
              - number
              - string
            tenant_id: *9
          required:
          - tenant_id
          - property_id
          - amount
          - category_id
          type: object
      responses:
        '200':
          description: Created invoice.
          schema: &10
            $schema: http://json-schema.org/draft-07/schema#
            additionalProperties: 0
            properties:
              amount:
                minimum: 0.01
                multipleOf: 0.01
                type: number
              category_id:
                description: 'Invoice category id. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency?version=v1.1#operation/get_invoice_categories).'
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              deposit_id:
                description: Tenant payment reference
                type: string
              description:
                maxLength: 255
                type:
                - string
                - 'null'
              end_date:
                example: '2021-09-24'
                format: date
                type:
                - string
                - 'null'
              frequency:
                description: 'Ref: [/api/docs/agency?version=v1.1#operation/get_transaction_frequencies](/api/docs/agency#operation/get_transaction_frequencies).'
                enum:
                - O
                - W
                - 2W
                - 4W
                - M
                - 2M
                - Q
                - 6M
                - A
                type: string
              has_invoice_period:
                description: Available for reoccurring invoices
                type: boolean
              has_tax:
                type: boolean
              id: &11
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              is_direct_debit:
                type: boolean
              payment_day:
                maximum: 31
                minimum: 1
                type: integer
              property_id: *11
              start_date:
                description: Default value of today if not provided.
                example: '2021-08-24'
                format: date
                type: string
              tax_amount:
                minimum: 0
                type:
                - number
                - string
              tenant_id: *11
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create invoice
      tags:
      - Invoice Entity
  /entity/invoice/{external_id}:
    get:
      description: "Get invoice\n"
      operationId: get_invoice
      parameters:
      - description: External ID of invoice.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Retrieved invoice.
          schema: *10
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get invoice
      tags:
      - Invoice Entity
    put:
      consumes:
      - application/json
      description: |
        Update invoice. This action will change the entity ID and you will be issued a new ID for subsequent requests.
      operationId: update_invoice
      parameters:
      - description: Invoice to update.
        in: body
        name: invoice
        schema:
          $schema: http://json-schema.org/draft-07/schema#
          additionalProperties: 0
          properties:
            amount:
              minimum: 0.01
              multipleOf: 0.01
              type: number
            category_id:
              description: 'Invoice category id. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency?version=v1.1#operation/get_invoice_categories).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            description:
              maxLength: 255
              type:
              - string
              - 'null'
            end_date:
              example: '2021-09-24'
              format: date
              type:
              - string
              - 'null'
            frequency:
              description: 'Ref: [/api/docs/agency?version=v1.1#operation/get_transaction_frequencies](/api/docs/agency#operation/get_transaction_frequencies).'
              enum:
              - O
              - W
              - 2W
              - 4W
              - M
              - 2M
              - Q
              - 6M
              - A
              type: string
            has_invoice_period:
              description: Available for reoccurring invoices
              type: boolean
            has_tax:
              type: boolean
            is_direct_debit:
              type: boolean
            payment_day:
              maximum: 31
              minimum: 1
              type: integer
            property_id: &12
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            start_date:
              description: Default value of today if not provided.
              example: '2021-08-24'
              format: date
              type: string
            tax_amount:
              minimum: 0
              type:
              - number
              - string
            tenant_id: *12
          type: object
      - description: External ID of invoice.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Updated invoice.
          schema: *10
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update invoice
      tags:
      - Invoice Entity
  /entity/payment:
    post:
      consumes:
      - application/json
      description: "Create new payment\n"
      operationId: create_payment
      parameters:
      - description: Payment to create.
        in: body
        name: payment
        schema:
          $schema: http://json-schema.org/draft-07/schema#
          additionalProperties: 0
          properties:
            amount:
              minimum: 0.01
              multipleOf: 0.01
              type: number
            beneficiary_id: &13
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            beneficiary_type:
              enum:
              - agency
              - beneficiary
              - global_beneficiary
              - property_account
              - deposit_account
              type: string
            category_id:
              description: 'Payment category id. Ref: [/api/docs/agency#operation/get_payment_categories](/api/docs/agency?version=v1.1#operation/get_payment_categories).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            description:
              maxLength: 255
              type:
              - string
              - 'null'
            enabled:
              type: boolean
            end_date:
              example: '2021-09-24'
              format: date
              type:
              - string
              - 'null'
            frequency:
              description: 'Ref: [/api/docs/agency#operation/get_transaction_frequencies](/api/docs/agency?version=v1.1#operation/get_transaction_frequencies).'
              enum:
              - O
              - W
              - 2W
              - 4W
              - M
              - 2M
              - Q
              - 6M
              - A
              type: string
            global_beneficiary:
              description: This field is deprecated and will be removed in the future.
                Please make use of the `beneficiary_id` field.
              maxLength: 100
              minLength: 2
              type: string
            has_tax:
              type: boolean
            maintenance_ticket_id:
              description: 'Maintenance ticket external ID to which new payments must
                be associated with. Ref: [/api/docs/agency#tag/Maintenance](/api/docs/agency?version=v1.1#tag/Maintenance).'
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type:
              - 'null'
              - string
            no_commission_amount:
              minimum: 0.01
              type:
              - number
              - 'null'
            payment_day:
              maximum: 31
              minimum: 0
              type: integer
            percentage:
              maximum: 100
              minimum: 0.01
              type:
              - number
              - string
            property_id: *13
            reference:
              description: Required if `beneficiary_type` is **beneficiary**.
              maxLength: 50
              type: string
            start_date:
              description: Default value of today if not provided.
              example: '2021-08-24'
              format: date
              type: string
            tax_amount:
              minimum: 0
              type:
              - number
              - string
            tenant_id: *13
            use_money_from:
              enum:
              - any_tenant
              - tenant
              - property_account
              type: string
          required:
          - property_id
          - beneficiary_type
          - frequency
          type: object
      responses:
        '200':
          description: Created payment.
          schema: &14
            $schema: http://json-schema.org/draft-07/schema#
            additionalProperties: 0
            properties:
              amount:
                minimum: 0.01
                multipleOf: 0.01
                type: number
              beneficiary_id: &15
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              beneficiary_type:
                enum:
                - agency
                - beneficiary
                - global_beneficiary
                - property_account
                - deposit_account
                type: string
              category_id:
                description: 'Payment category id. Ref: [/api/docs/agency#operation/get_payment_categories](/api/docs/agency?version=v1.1#operation/get_payment_categories).'
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              description:
                maxLength: 255
                type:
                - string
                - 'null'
              enabled:
                type: boolean
              end_date:
                example: '2021-09-24'
                format: date
                type:
                - string
                - 'null'
              frequency:
                description: 'Ref: [/api/docs/agency#operation/get_transaction_frequencies](/api/docs/agency?version=v1.1#operation/get_transaction_frequencies).'
                enum:
                - O
                - W
                - 2W
                - 4W
                - M
                - 2M
                - Q
                - 6M
                - A
                type: string
              global_beneficiary:
                description: This field is deprecated and will be removed in the future.
                  Please make use of the `beneficiary_id` field.
                maxLength: 100
                minLength: 2
                type: string
              has_tax:
                type: boolean
              id: *15
              maintenance_ticket_id:
                description: 'Maintenance ticket external ID to which new payments
                  must be associated with. Ref: [/api/docs/agency#tag/Maintenance](/api/docs/agency?version=v1.1#tag/Maintenance).'
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type:
                - 'null'
                - string
              no_commission_amount:
                minimum: 0.01
                type:
                - number
                - 'null'
              payment_day:
                maximum: 31
                minimum: 0
                type: integer
              percentage:
                maximum: 100
                minimum: 0.01
                type:
                - number
                - string
              property_id: *15
              reference:
                description: Required if `beneficiary_type` is **beneficiary**.
                maxLength: 50
                type: string
              start_date:
                description: Default value of today if not provided.
                example: '2021-08-24'
                format: date
                type: string
              tax_amount:
                minimum: 0
                type:
                - number
                - string
              tenant_id: *15
              use_money_from:
                enum:
                - any_tenant
                - tenant
                - property_account
                type: string
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create payment
      tags:
      - Payment Entity
  /entity/payment/{external_id}:
    get:
      description: "Get payment\n"
      operationId: get_payment
      parameters:
      - description: External ID of payment.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Retrieved payment.
          schema: *14
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get payment
      tags:
      - Payment Entity
    put:
      consumes:
      - application/json
      description: |
        Update payment. This action will change the entity ID and you will be issued a new ID for subsequent requests.
      operationId: update_payment
      parameters:
      - description: Payment to update.
        in: body
        name: payment
        schema:
          $schema: http://json-schema.org/draft-07/schema#
          additionalProperties: 0
          properties:
            amount:
              minimum: 0.01
              multipleOf: 0.01
              type: number
            beneficiary_id: &16
              example: 'D8eJPwZG7j'
              maxLength: 32
              minLength: 10
              pattern: ^[a-zA-Z0-9]+$
              type: string
            beneficiary_type:
              enum:
              - agency
              - beneficiary
              - global_beneficiary
              - property_account
              - deposit_account
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            description:
              maxLength: 255
              type:
              - string
              - 'null'
            enabled:
              type: boolean
            end_date:
              example: '2021-09-24'
              format: date
              type:
              - string
              - 'null'
            frequency:
              description: 'Ref: [/api/docs/agency#operation/get_transaction_frequencies](/api/docs/agency?version=v1.1#operation/get_transaction_frequencies).'
              enum:
              - O
              - W
              - 2W
              - 4W
              - M
              - 2M
              - Q
              - 6M
              - A
              type: string
            global_beneficiary:
              description: This field is deprecated and will be removed in the future.
                Please make use of the `beneficiary_id` field.
              maxLength: 100
              minLength: 2
              type: string
            has_tax:
              type: boolean
            no_commission_amount:
              minimum: 0.01
              type:
              - number
              - 'null'
            payment_day:
              maximum: 31
              minimum: 0
              type: integer
            percentage:
              maximum: 100
              minimum: 0.01
              type:
              - number
              - string
            property_id: *16
            reference:
              description: Required if `beneficiary_type` is **beneficiary**.
              maxLength: 50
              type: string
            start_date:
              description: Default value of today if not provided.
              example: '2021-08-24'
              format: date
              type: string
            tax_amount:
              minimum: 0
              type:
              - number
              - string
            tenant_id: *16
            use_money_from:
              enum:
              - any_tenant
              - tenant
              - property_account
              type: string
          type: object
      - description: External ID of payment.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Updated payment.
          schema: *14
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update payment
      tags:
      - Payment Entity
  /entity/property:
    post:
      consumes:
      - application/json
      description: "Create new property\n"
      operationId: create_property
      parameters:
      - description: Property to create.
        in: body
        name: property
        schema:
          $schema: http://json-schema.org/schema#
          additionalProperties: 0
          properties:
            address:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The address that will be used on tenant communications.
                Typically this is the rental address, but can be a guarantor.
              properties:
                address_line_1:
                  maxLength: 50
                  type: string
                address_line_2:
                  maxLength: 50
                  type: string
                address_line_3:
                  maxLength: 50
                  type: string
                city:
                  maxLength: 50
                  type: string
                country_code:
                  default: UK
                  enum:
                  - AD
                  - AE
                  - AF
                  - AG
                  - AI
                  - AL
                  - AM
                  - AN
                  - AO
                  - AQ
                  - AR
                  - AS
                  - AT
                  - AU
                  - AW
                  - AX
                  - AZ
                  - BA
                  - BB
                  - BD
                  - BE
                  - BF
                  - BG
                  - BH
                  - BI
                  - BJ
                  - BL
                  - BM
                  - BN
                  - BO
                  - BQ
                  - BR
                  - BS
                  - BT
                  - BV
                  - BW
                  - BY
                  - BZ
                  - CA
                  - CC
                  - CD
                  - CF
                  - CG
                  - CH
                  - CI
                  - CK
                  - CL
                  - CM
                  - CN
                  - CO
                  - CR
                  - CU
                  - CV
                  - CW
                  - CX
                  - CY
                  - CZ
                  - DE
                  - DJ
                  - DK
                  - DM
                  - DO
                  - DZ
                  - EC
                  - EE
                  - EG
                  - EH
                  - ER
                  - ES
                  - ET
                  - FI
                  - FJ
                  - FK
                  - FM
                  - FO
                  - FR
                  - GA
                  - GD
                  - GE
                  - GF
                  - GG
                  - GH
                  - GI
                  - GL
                  - GM
                  - GN
                  - GP
                  - GQ
                  - GR
                  - GS
                  - GT
                  - GU
                  - GW
                  - GY
                  - HK
                  - HM
                  - HN
                  - HR
                  - HT
                  - HU
                  - ID
                  - IE
                  - IL
                  - IM
                  - IN
                  - IO
                  - IQ
                  - IR
                  - IS
                  - IT
                  - JE
                  - JM
                  - JO
                  - JP
                  - KE
                  - KG
                  - KH
                  - KI
                  - KM
                  - KN
                  - KP
                  - KR
                  - KW
                  - KY
                  - KZ
                  - LA
                  - LB
                  - LC
                  - LI
                  - LK
                  - LR
                  - LS
                  - LT
                  - LU
                  - LV
                  - LY
                  - MA
                  - MC
                  - MD
                  - ME
                  - MF
                  - MG
                  - MH
                  - MK
                  - ML
                  - MM
                  - MN
                  - MO
                  - MP
                  - MQ
                  - MR
                  - MS
                  - MT
                  - MU
                  - MV
                  - MW
                  - MX
                  - MY
                  - MZ
                  - NA
                  - NC
                  - NE
                  - NF
                  - NG
                  - NI
                  - NL
                  - NO
                  - NP
                  - NR
                  - NU
                  - NZ
                  - OM
                  - PA
                  - PE
                  - PF
                  - PG
                  - PH
                  - PK
                  - PL
                  - PM
                  - PN
                  - PR
                  - PS
                  - PT
                  - PW
                  - PY
                  - QA
                  - RE
                  - RO
                  - RS
                  - RU
                  - RW
                  - SA
                  - SB
                  - SC
                  - SD
                  - SE
                  - SG
                  - SH
                  - SI
                  - SJ
                  - SK
                  - SL
                  - SM
                  - SN
                  - SO
                  - SR
                  - SS
                  - ST
                  - SV
                  - SX
                  - SY
                  - SZ
                  - TC
                  - TD
                  - TF
                  - TG
                  - TH
                  - TJ
                  - TK
                  - TL
                  - TM
                  - TN
                  - TO
                  - TP
                  - TR
                  - TT
                  - TV
                  - TW
                  - TZ
                  - UA
                  - UG
                  - UK
                  - UM
                  - US
                  - UY
                  - UZ
                  - VA
                  - VC
                  - VE
                  - VG
                  - VI
                  - VN
                  - VU
                  - WF
                  - WS
                  - YE
                  - YT
                  - YU
                  - ZA
                  - ZM
                  - ZR
                  - ZW
                  type: string
                latitude:
                  maxLength: 50
                  type: string
                longitude:
                  maxLength: 50
                  type: string
                postal_code:
                  maxLength: 10
                  type: string
                state:
                  maxLength: 50
                  type: string
              type: object
            agent_name:
              description: Values can be entered her to subdivide your portfolio.
                For example, you can enter different branch names or service levels.
                The income report will then give separate subtotals for each unique
                value.
              maxLength: 50
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            customer_reference:
              description: Customer reference of property.
              maxLength: 50
              type: string
            name:
              description: The property name is how your property will be indexed,
                and searched. We recommend using the first line of the address. This
                must be unique to this specific property.
              maxLength: 255
              pattern: ^.*\S.*$
              type: string
            notes:
              maxLength: 60000
              type: string
            responsible_agent_id:
              description: PayProp users that do not have the permission access all
                properties will only be able to see a property if they are set as
                the responsible agent. Only one user can be responsible for a property.
              maxLength: 32
              type:
              - string
              - 'null'
            settings:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              properties:
                enable_payments:
                  description: This determines whether funds reconciled to this property
                    can be paid out to beneficiaries. If it is set to false you can
                    still reconcile to this property, but not outgoing payments will
                    be made.
                  type: boolean
                hold_owner_funds:
                  description: If set to true then all funds that would have been
                    paid to the owner will be paid into the property account instead.
                  type: boolean
                listing_from:
                  description: Default value of today if not provided.
                  example: '2021-08-24'
                  format: date
                  type: string
                listing_to:
                  example: '2021-09-24'
                  format: date
                  type:
                  - string
                  - 'null'
                minimum_balance:
                  default: 0
                  description: This determines the minimum balance to be deducted
                    from the owners funds to be held in the property account. If funds
                    are released from the property account, subsequent payments to
                    the owner will be diverted to the property account until the minimum
                    balance is reached.
                  type: number
                monthly_payment:
                  description: The estimation of the rent you think the property can
                    achieve. This will then be visible on the Active tenants report,
                    where you can see how it compares to the actual rent.
                  type: number
                verify_payments:
                  type: boolean
              required:
              - monthly_payment
              type: object
            tax_group_id:
              description: This field is deprecated and will be removed in future.
              type:
              - integer
              - 'null'
          required:
          - name
          type: object
      responses:
        '200':
          description: Created property.
          schema: &17
            $schema: http://json-schema.org/schema#
            additionalProperties: 0
            properties:
              address:
                $schema: http://json-schema.org/schema#
                additionalProperties: 0
                description: The address that will be used on tenant communications.
                  Typically this is the rental address, but can be a guarantor.
                properties:
                  address_line_1:
                    maxLength: 50
                    type: string
                  address_line_2:
                    maxLength: 50
                    type: string
                  address_line_3:
                    maxLength: 50
                    type: string
                  city:
                    maxLength: 50
                    type: string
                  country_code:
                    enum:
                    - AD
                    - AE
                    - AF
                    - AG
                    - AI
                    - AL
                    - AM
                    - AN
                    - AO
                    - AQ
                    - AR
                    - AS
                    - AT
                    - AU
                    - AW
                    - AX
                    - AZ
                    - BA
                    - BB
                    - BD
                    - BE
                    - BF
                    - BG
                    - BH
                    - BI
                    - BJ
                    - BL
                    - BM
                    - BN
                    - BO
                    - BQ
                    - BR
                    - BS
                    - BT
                    - BV
                    - BW
                    - BY
                    - BZ
                    - CA
                    - CC
                    - CD
                    - CF
                    - CG
                    - CH
                    - CI
                    - CK
                    - CL
                    - CM
                    - CN
                    - CO
                    - CR
                    - CU
                    - CV
                    - CW
                    - CX
                    - CY
                    - CZ
                    - DE
                    - DJ
                    - DK
                    - DM
                    - DO
                    - DZ
                    - EC
                    - EE
                    - EG
                    - EH
                    - ER
                    - ES
                    - ET
                    - FI
                    - FJ
                    - FK
                    - FM
                    - FO
                    - FR
                    - GA
                    - GD
                    - GE
                    - GF
                    - GG
                    - GH
                    - GI
                    - GL
                    - GM
                    - GN
                    - GP
                    - GQ
                    - GR
                    - GS
                    - GT
                    - GU
                    - GW
                    - GY
                    - HK
                    - HM
                    - HN
                    - HR
                    - HT
                    - HU
                    - ID
                    - IE
                    - IL
                    - IM
                    - IN
                    - IO
                    - IQ
                    - IR
                    - IS
                    - IT
                    - JE
                    - JM
                    - JO
                    - JP
                    - KE
                    - KG
                    - KH
                    - KI
                    - KM
                    - KN
                    - KP
                    - KR
                    - KW
                    - KY
                    - KZ
                    - LA
                    - LB
                    - LC
                    - LI
                    - LK
                    - LR
                    - LS
                    - LT
                    - LU
                    - LV
                    - LY
                    - MA
                    - MC
                    - MD
                    - ME
                    - MF
                    - MG
                    - MH
                    - MK
                    - ML
                    - MM
                    - MN
                    - MO
                    - MP
                    - MQ
                    - MR
                    - MS
                    - MT
                    - MU
                    - MV
                    - MW
                    - MX
                    - MY
                    - MZ
                    - NA
                    - NC
                    - NE
                    - NF
                    - NG
                    - NI
                    - NL
                    - NO
                    - NP
                    - NR
                    - NU
                    - NZ
                    - OM
                    - PA
                    - PE
                    - PF
                    - PG
                    - PH
                    - PK
                    - PL
                    - PM
                    - PN
                    - PR
                    - PS
                    - PT
                    - PW
                    - PY
                    - QA
                    - RE
                    - RO
                    - RS
                    - RU
                    - RW
                    - SA
                    - SB
                    - SC
                    - SD
                    - SE
                    - SG
                    - SH
                    - SI
                    - SJ
                    - SK
                    - SL
                    - SM
                    - SN
                    - SO
                    - SR
                    - SS
                    - ST
                    - SV
                    - SX
                    - SY
                    - SZ
                    - TC
                    - TD
                    - TF
                    - TG
                    - TH
                    - TJ
                    - TK
                    - TL
                    - TM
                    - TN
                    - TO
                    - TP
                    - TR
                    - TT
                    - TV
                    - TW
                    - TZ
                    - UA
                    - UG
                    - UK
                    - UM
                    - US
                    - UY
                    - UZ
                    - VA
                    - VC
                    - VE
                    - VG
                    - VI
                    - VN
                    - VU
                    - WF
                    - WS
                    - YE
                    - YT
                    - YU
                    - ZA
                    - ZM
                    - ZR
                    - ZW
                    type: string
                  latitude:
                    maxLength: 50
                    type: string
                  longitude:
                    maxLength: 50
                    type: string
                  postal_code:
                    maxLength: 10
                    type: string
                  state:
                    maxLength: 50
                    type: string
                type: object
              agent_name:
                description: Values can be entered her to subdivide your portfolio.
                  For example, you can enter different branch names or service levels.
                  The income report will then give separate subtotals for each unique
                  value.
                maxLength: 50
                type: string
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              customer_reference:
                description: Customer reference of property.
                maxLength: 50
                type: string
              id:
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              name:
                description: The property name is how your property will be indexed,
                  and searched. We recommend using the first line of the address.
                  This must be unique to this specific property.
                maxLength: 255
                pattern: ^.*\S.*$
                type: string
              notes:
                maxLength: 60000
                type: string
              responsible_agent_id:
                description: PayProp users that do not have the permission access
                  all properties will only be able to see a property if they are set
                  as the responsible agent. Only one user can be responsible for a
                  property.
                maxLength: 32
                type:
                - string
                - 'null'
              settings:
                $schema: http://json-schema.org/schema#
                additionalProperties: 0
                properties:
                  enable_payments:
                    description: This determines whether funds reconciled to this
                      property can be paid out to beneficiaries. If it is set to false
                      you can still reconcile to this property, but not outgoing payments
                      will be made.
                    type: boolean
                  hold_owner_funds:
                    description: If set to true then all funds that would have been
                      paid to the owner will be paid into the property account instead.
                    type: boolean
                  listing_from:
                    description: Default value of today if not provided.
                    example: '2021-08-24'
                    format: date
                    type: string
                  listing_to:
                    example: '2021-09-24'
                    format: date
                    type:
                    - string
                    - 'null'
                  minimum_balance:
                    description: This determines the minimum balance to be deducted
                      from the owners funds to be held in the property account. If
                      funds are released from the property account, subsequent payments
                      to the owner will be diverted to the property account until
                      the minimum balance is reached.
                    type: number
                  monthly_payment:
                    description: The estimation of the rent you think the property
                      can achieve. This will then be visible on the Active tenants
                      report, where you can see how it compares to the actual rent.
                    type: number
                  verify_payments:
                    type: boolean
                type: object
              tax_group_id:
                description: This field is deprecated and will be removed in future.
                type:
                - integer
                - 'null'
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create property
      tags:
      - Property Entity
  /entity/property/{external_id}:
    get:
      description: "Get property\n"
      operationId: get_property
      parameters:
      - description: External ID of property.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Retrieved property.
          schema: *17
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get property
      tags:
      - Property Entity
    put:
      consumes:
      - application/json
      description: "Update existing property\n"
      operationId: update_property
      parameters:
      - description: Property to update.
        in: body
        name: property
        schema:
          $schema: http://json-schema.org/schema#
          additionalProperties: 0
          properties:
            address:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The address that will be used on tenant communications.
                Typically this is the rental address, but can be a guarantor.
              properties:
                address_line_1:
                  maxLength: 50
                  type: string
                address_line_2:
                  maxLength: 50
                  type: string
                address_line_3:
                  maxLength: 50
                  type: string
                city:
                  maxLength: 50
                  type: string
                country_code:
                  enum:
                  - AD
                  - AE
                  - AF
                  - AG
                  - AI
                  - AL
                  - AM
                  - AN
                  - AO
                  - AQ
                  - AR
                  - AS
                  - AT
                  - AU
                  - AW
                  - AX
                  - AZ
                  - BA
                  - BB
                  - BD
                  - BE
                  - BF
                  - BG
                  - BH
                  - BI
                  - BJ
                  - BL
                  - BM
                  - BN
                  - BO
                  - BQ
                  - BR
                  - BS
                  - BT
                  - BV
                  - BW
                  - BY
                  - BZ
                  - CA
                  - CC
                  - CD
                  - CF
                  - CG
                  - CH
                  - CI
                  - CK
                  - CL
                  - CM
                  - CN
                  - CO
                  - CR
                  - CU
                  - CV
                  - CW
                  - CX
                  - CY
                  - CZ
                  - DE
                  - DJ
                  - DK
                  - DM
                  - DO
                  - DZ
                  - EC
                  - EE
                  - EG
                  - EH
                  - ER
                  - ES
                  - ET
                  - FI
                  - FJ
                  - FK
                  - FM
                  - FO
                  - FR
                  - GA
                  - GD
                  - GE
                  - GF
                  - GG
                  - GH
                  - GI
                  - GL
                  - GM
                  - GN
                  - GP
                  - GQ
                  - GR
                  - GS
                  - GT
                  - GU
                  - GW
                  - GY
                  - HK
                  - HM
                  - HN
                  - HR
                  - HT
                  - HU
                  - ID
                  - IE
                  - IL
                  - IM
                  - IN
                  - IO
                  - IQ
                  - IR
                  - IS
                  - IT
                  - JE
                  - JM
                  - JO
                  - JP
                  - KE
                  - KG
                  - KH
                  - KI
                  - KM
                  - KN
                  - KP
                  - KR
                  - KW
                  - KY
                  - KZ
                  - LA
                  - LB
                  - LC
                  - LI
                  - LK
                  - LR
                  - LS
                  - LT
                  - LU
                  - LV
                  - LY
                  - MA
                  - MC
                  - MD
                  - ME
                  - MF
                  - MG
                  - MH
                  - MK
                  - ML
                  - MM
                  - MN
                  - MO
                  - MP
                  - MQ
                  - MR
                  - MS
                  - MT
                  - MU
                  - MV
                  - MW
                  - MX
                  - MY
                  - MZ
                  - NA
                  - NC
                  - NE
                  - NF
                  - NG
                  - NI
                  - NL
                  - NO
                  - NP
                  - NR
                  - NU
                  - NZ
                  - OM
                  - PA
                  - PE
                  - PF
                  - PG
                  - PH
                  - PK
                  - PL
                  - PM
                  - PN
                  - PR
                  - PS
                  - PT
                  - PW
                  - PY
                  - QA
                  - RE
                  - RO
                  - RS
                  - RU
                  - RW
                  - SA
                  - SB
                  - SC
                  - SD
                  - SE
                  - SG
                  - SH
                  - SI
                  - SJ
                  - SK
                  - SL
                  - SM
                  - SN
                  - SO
                  - SR
                  - SS
                  - ST
                  - SV
                  - SX
                  - SY
                  - SZ
                  - TC
                  - TD
                  - TF
                  - TG
                  - TH
                  - TJ
                  - TK
                  - TL
                  - TM
                  - TN
                  - TO
                  - TP
                  - TR
                  - TT
                  - TV
                  - TW
                  - TZ
                  - UA
                  - UG
                  - UK
                  - UM
                  - US
                  - UY
                  - UZ
                  - VA
                  - VC
                  - VE
                  - VG
                  - VI
                  - VN
                  - VU
                  - WF
                  - WS
                  - YE
                  - YT
                  - YU
                  - ZA
                  - ZM
                  - ZR
                  - ZW
                  type: string
                latitude:
                  maxLength: 50
                  type: string
                longitude:
                  maxLength: 50
                  type: string
                postal_code:
                  maxLength: 10
                  type: string
                state:
                  maxLength: 50
                  type: string
              type: object
            agent_name:
              description: Values can be entered her to subdivide your portfolio.
                For example, you can enter different branch names or service levels.
                The income report will then give separate subtotals for each unique
                value.
              maxLength: 50
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            customer_reference:
              description: Customer reference of property.
              maxLength: 50
              type: string
            name:
              description: The property name is how your property will be indexed,
                and searched. We recommend using the first line of the address. This
                must be unique to this specific property.
              maxLength: 255
              pattern: ^.*\S.*$
              type: string
            notes:
              maxLength: 60000
              type: string
            responsible_agent_id:
              description: PayProp users that do not have the permission access all
                properties will only be able to see a property if they are set as
                the responsible agent. Only one user can be responsible for a property.
              maxLength: 32
              type:
              - string
              - 'null'
            settings:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              properties:
                enable_payments:
                  description: This determines whether funds reconciled to this property
                    can be paid out to beneficiaries. If it is set to false you can
                    still reconcile to this property, but not outgoing payments will
                    be made.
                  type: boolean
                hold_owner_funds:
                  description: If set to true then all funds that would have been
                    paid to the owner will be paid into the property account instead.
                  type: boolean
                listing_from:
                  description: Default value of today if not provided.
                  example: '2021-08-24'
                  format: date
                  type: string
                listing_to:
                  example: '2021-09-24'
                  format: date
                  type:
                  - string
                  - 'null'
                minimum_balance:
                  description: This determines the minimum balance to be deducted
                    from the owners funds to be held in the property account. If funds
                    are released from the property account, subsequent payments to
                    the owner will be diverted to the property account until the minimum
                    balance is reached.
                  type: number
                monthly_payment:
                  description: The estimation of the rent you think the property can
                    achieve. This will then be visible on the Active tenants report,
                    where you can see how it compares to the actual rent.
                  type: number
                verify_payments:
                  type: boolean
              type: object
            tax_group_id:
              description: This field is deprecated and will be removed in future.
              type:
              - integer
              - 'null'
          type: object
      - description: External ID of property.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Updated property.
          schema: *17
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update property
      tags:
      - Property Entity
  /entity/secondary-payment:
    post:
      consumes:
      - application/json
      description: "Create new secondary payment.\n"
      operationId: create_secondary_payment
      parameters:
      - description: Secondary payment to create.
        in: body
        name: secondary payment
        schema:
          $ref: api_definitions.yaml#/definitions/SecondaryPaymentCreate
      responses:
        '200':
          description: Created secondary payment.
          schema:
            $ref: api_definitions.yaml#/definitions/SecondaryPaymentItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create secondary payment
      tags:
      - Secondary Payment
  /entity/secondary-payment/{external_id}:
    get:
      consumes:
      - application/json
      description: "Return secondary payment.\n"
      operationId: get_secondary_payment
      parameters:
      - description: External ID of secondary payment.
        in: path
        maxLength: 32
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Secondary payment.
          schema:
            $ref: api_definitions.yaml#/definitions/SecondaryPaymentItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get secondary payment
      tags:
      - Secondary Payment
    put:
      consumes:
      - application/json
      description: "Update secondary payment.\n"
      operationId: update_secondary_payment
      parameters:
      - description: Secondary payment to update.
        in: body
        name: secondary payment
        schema:
          $ref: api_definitions.yaml#/definitions/SecondaryPaymentUpdate
      - description: External ID of secondary payment.
        in: path
        maxLength: 32
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Updated secondary payment.
          schema:
            $ref: api_definitions.yaml#/definitions/SecondaryPaymentItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update secondary payment
      tags:
      - Secondary Payment
  /entity/tenant:
    post:
      consumes:
      - application/json
      description: "Create new tenant\n"
      operationId: create_tenant
      parameters:
      - description: Tenant to create.
        in: body
        name: tenant
        schema:
          $schema: http://json-schema.org/schema#
          additionalProperties: 0
          allOf:
          - if:
              properties:
                account_type:
                  const: individual
            then:
              properties:
                first_name:
                  minLength: 2
                last_name:
                  minLength: 2
              required:
              - first_name
              - last_name
          - if:
              properties:
                account_type:
                  const: business
            then:
              properties:
                business_name:
                  minLength: 2
              required:
              - business_name
          - if:
              properties:
                has_bank_account:
                  const: 1
            then:
              properties:
                bank_account:
                  required:
                  - account_name
                  - branch_code
                  - account_number
              required:
              - bank_account
          - if:
              properties:
                mobile_number:
                  minLength: 1
            then:
              properties:
                mobile_number:
                  pattern: ^[1-9]\d+$
          properties:
            account_type:
              default: individual
              description: This determines how the tenant is addressed on their communications.
                Individuals are addressed by the `first_name` and `last_name` field,
                whereas a business is addressed by the `business_name` field.
              enum:
              - individual
              - business
              type: string
            address:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The address that will be used on tenant communications.
                Typically this is the rental address, but can be a guarantor.
              properties:
                address_line_1:
                  maxLength: 50
                  type: string
                address_line_2:
                  maxLength: 50
                  type: string
                address_line_3:
                  maxLength: 50
                  type: string
                city:
                  maxLength: 50
                  type: string
                country_code:
                  default: UK
                  enum:
                  - AD
                  - AE
                  - AF
                  - AG
                  - AI
                  - AL
                  - AM
                  - AN
                  - AO
                  - AQ
                  - AR
                  - AS
                  - AT
                  - AU
                  - AW
                  - AX
                  - AZ
                  - BA
                  - BB
                  - BD
                  - BE
                  - BF
                  - BG
                  - BH
                  - BI
                  - BJ
                  - BL
                  - BM
                  - BN
                  - BO
                  - BQ
                  - BR
                  - BS
                  - BT
                  - BV
                  - BW
                  - BY
                  - BZ
                  - CA
                  - CC
                  - CD
                  - CF
                  - CG
                  - CH
                  - CI
                  - CK
                  - CL
                  - CM
                  - CN
                  - CO
                  - CR
                  - CU
                  - CV
                  - CW
                  - CX
                  - CY
                  - CZ
                  - DE
                  - DJ
                  - DK
                  - DM
                  - DO
                  - DZ
                  - EC
                  - EE
                  - EG
                  - EH
                  - ER
                  - ES
                  - ET
                  - FI
                  - FJ
                  - FK
                  - FM
                  - FO
                  - FR
                  - GA
                  - GD
                  - GE
                  - GF
                  - GG
                  - GH
                  - GI
                  - GL
                  - GM
                  - GN
                  - GP
                  - GQ
                  - GR
                  - GS
                  - GT
                  - GU
                  - GW
                  - GY
                  - HK
                  - HM
                  - HN
                  - HR
                  - HT
                  - HU
                  - ID
                  - IE
                  - IL
                  - IM
                  - IN
                  - IO
                  - IQ
                  - IR
                  - IS
                  - IT
                  - JE
                  - JM
                  - JO
                  - JP
                  - KE
                  - KG
                  - KH
                  - KI
                  - KM
                  - KN
                  - KP
                  - KR
                  - KW
                  - KY
                  - KZ
                  - LA
                  - LB
                  - LC
                  - LI
                  - LK
                  - LR
                  - LS
                  - LT
                  - LU
                  - LV
                  - LY
                  - MA
                  - MC
                  - MD
                  - ME
                  - MF
                  - MG
                  - MH
                  - MK
                  - ML
                  - MM
                  - MN
                  - MO
                  - MP
                  - MQ
                  - MR
                  - MS
                  - MT
                  - MU
                  - MV
                  - MW
                  - MX
                  - MY
                  - MZ
                  - NA
                  - NC
                  - NE
                  - NF
                  - NG
                  - NI
                  - NL
                  - NO
                  - NP
                  - NR
                  - NU
                  - NZ
                  - OM
                  - PA
                  - PE
                  - PF
                  - PG
                  - PH
                  - PK
                  - PL
                  - PM
                  - PN
                  - PR
                  - PS
                  - PT
                  - PW
                  - PY
                  - QA
                  - RE
                  - RO
                  - RS
                  - RU
                  - RW
                  - SA
                  - SB
                  - SC
                  - SD
                  - SE
                  - SG
                  - SH
                  - SI
                  - SJ
                  - SK
                  - SL
                  - SM
                  - SN
                  - SO
                  - SR
                  - SS
                  - ST
                  - SV
                  - SX
                  - SY
                  - SZ
                  - TC
                  - TD
                  - TF
                  - TG
                  - TH
                  - TJ
                  - TK
                  - TL
                  - TM
                  - TN
                  - TO
                  - TP
                  - TR
                  - TT
                  - TV
                  - TW
                  - TZ
                  - UA
                  - UG
                  - UK
                  - UM
                  - US
                  - UY
                  - UZ
                  - VA
                  - VC
                  - VE
                  - VG
                  - VI
                  - VN
                  - VU
                  - WF
                  - WS
                  - YE
                  - YT
                  - YU
                  - ZA
                  - ZM
                  - ZR
                  - ZW
                  type: string
                latitude:
                  maxLength: 50
                  type: string
                longitude:
                  maxLength: 50
                  type: string
                postal_code:
                  maxLength: 10
                  type: string
                state:
                  maxLength: 50
                  type: string
              type: object
            bank_account:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The bank details of a tenant can be provided for record
                keeping purposes. Tenants cannot be paid using these bank details.
              properties:
                account_name:
                  maxLength: 50
                  minLength: 1
                  type: string
                account_number:
                  maxLength: 8
                  minLength: 3
                  pattern: ^\d+$
                  type: string
                bank_name:
                  maxLength: 50
                  type: string
                branch_code:
                  maxLength: 6
                  minLength: 6
                  pattern: ^\d+$
                  type: string
                branch_name:
                  maxLength: 50
                  type: string
              type: object
            business_name:
              description: Required if account_type is business.
              maxLength: 50
              type: string
            comment:
              maxLength: 6000
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            customer_reference:
              description: A unique value you can add to identify the tenant. This
                is visible on the platform. This can be used to enhance the accuracy
                of recommendations in the Unreconciled payments screen.
              maxLength: 50
              type: string
            date_of_birth:
              example: '1991-08-24'
              format: date
              type:
              - string
              - 'null'
            email_address:
              description: Specify the email address that you would like the tenant's
                communications to go to. This includes invoices, arrears reminders,
                arrears letters, rent receipts, and monthly statements if this is
                activated by the agent. This also gives access to the tenant portal.
              format: email
              maxLength: 50
              type:
              - string
              - 'null'
            email_cc:
              description: Up to 10 email addresses can be listed here, recipients
                will be copied on the tenant's communications but will not be given
                access to the tenant portal.
              items:
                format: email
                type: string
              maxItems: 10
              type: array
            fax:
              maxLength: 15
              type: string
            first_name:
              description: Required if account_type is individual.
              maxLength: 50
              type: string
            has_bank_account:
              description: Required if updating bank account. Setting the field to
                false will remove existing bank account details.
              type: boolean
            id_number:
              description: Personal identification number from e.g. Passport/driving
                license.
              maxLength: 15
              type: string
            id_type_id:
              default: ~
              description: 'ID Type external id. Ref: [/api/docs/agency?version=v1.1#operation/get_entity_id_types](/api/docs/agency?version=v1.1#operation/get_entity_id_types).'
              maxLength: 32
              type:
              - string
              - 'null'
            last_name:
              description: Required if account_type is individual.
              maxLength: 50
              type: string
            lead_days:
              default: 0
              description: "Tenant invoices can be sent ahead of their due date, a
                `lead_days` value of 2 will send invoices 2 days in advance. Please
                note this can in some cases lead to invoices not being sent and will
                require a single frequency invoice to replace it. We recommend following
                these rules:\n\t\t\t\t<ul>\n\t\t\t\t\t<li>If invoice date minus `lead_days`
                is less than or equal to today, then send a single invoice to replace
                the first invoice.</li>\n\t\t\t\t\t<li>Only update the invoice start
                date if you want to skip one or more invoices, otherwise just leave
                it blank.</li>\n\t\t\t\t\t<li>Then just account for month ends, so
                if the on day is one and the lead days are 2, you will end up with
                30, or 29 etc.</li>\n\t\t\t\t</ul>"
              maximum: 31
              type: integer
            mobile_number:
              description: The mobile phone number you would like receive notifications
                regarding this tenant.
              maxLength: 15
              type:
              - string
              - 'null'
            notify_email:
              type: boolean
            notify_sms:
              type: boolean
            phone:
              maxLength: 15
              type: string
            vat_number:
              maxLength: 50
              type: string
          required: []
          type: object
      responses:
        '200':
          description: Created tenant.
          schema: &18
            $schema: http://json-schema.org/schema#
            additionalProperties: 0
            allOf:
            - if:
                properties:
                  account_type:
                    const: individual
              then:
                properties:
                  first_name:
                    minLength: 2
                  last_name:
                    minLength: 2
            - if:
                properties:
                  account_type:
                    const: business
              then:
                properties:
                  business_name:
                    minLength: 2
            - if:
                properties:
                  has_bank_account:
                    const: 1
              then:
                properties:
                  bank_account: {}
            - if:
                properties:
                  mobile_number:
                    minLength: 1
              then:
                properties:
                  mobile_number:
                    pattern: ^[1-9]\d+$
            properties:
              account_type:
                description: This determines how the tenant is addressed on their
                  communications. Individuals are addressed by the `first_name` and
                  `last_name` field, whereas a business is addressed by the `business_name`
                  field.
                enum:
                - individual
                - business
                type: string
              address:
                $schema: http://json-schema.org/schema#
                additionalProperties: 0
                description: The address that will be used on tenant communications.
                  Typically this is the rental address, but can be a guarantor.
                properties:
                  address_line_1:
                    maxLength: 50
                    type: string
                  address_line_2:
                    maxLength: 50
                    type: string
                  address_line_3:
                    maxLength: 50
                    type: string
                  city:
                    maxLength: 50
                    type: string
                  country_code:
                    enum:
                    - AD
                    - AE
                    - AF
                    - AG
                    - AI
                    - AL
                    - AM
                    - AN
                    - AO
                    - AQ
                    - AR
                    - AS
                    - AT
                    - AU
                    - AW
                    - AX
                    - AZ
                    - BA
                    - BB
                    - BD
                    - BE
                    - BF
                    - BG
                    - BH
                    - BI
                    - BJ
                    - BL
                    - BM
                    - BN
                    - BO
                    - BQ
                    - BR
                    - BS
                    - BT
                    - BV
                    - BW
                    - BY
                    - BZ
                    - CA
                    - CC
                    - CD
                    - CF
                    - CG
                    - CH
                    - CI
                    - CK
                    - CL
                    - CM
                    - CN
                    - CO
                    - CR
                    - CU
                    - CV
                    - CW
                    - CX
                    - CY
                    - CZ
                    - DE
                    - DJ
                    - DK
                    - DM
                    - DO
                    - DZ
                    - EC
                    - EE
                    - EG
                    - EH
                    - ER
                    - ES
                    - ET
                    - FI
                    - FJ
                    - FK
                    - FM
                    - FO
                    - FR
                    - GA
                    - GD
                    - GE
                    - GF
                    - GG
                    - GH
                    - GI
                    - GL
                    - GM
                    - GN
                    - GP
                    - GQ
                    - GR
                    - GS
                    - GT
                    - GU
                    - GW
                    - GY
                    - HK
                    - HM
                    - HN
                    - HR
                    - HT
                    - HU
                    - ID
                    - IE
                    - IL
                    - IM
                    - IN
                    - IO
                    - IQ
                    - IR
                    - IS
                    - IT
                    - JE
                    - JM
                    - JO
                    - JP
                    - KE
                    - KG
                    - KH
                    - KI
                    - KM
                    - KN
                    - KP
                    - KR
                    - KW
                    - KY
                    - KZ
                    - LA
                    - LB
                    - LC
                    - LI
                    - LK
                    - LR
                    - LS
                    - LT
                    - LU
                    - LV
                    - LY
                    - MA
                    - MC
                    - MD
                    - ME
                    - MF
                    - MG
                    - MH
                    - MK
                    - ML
                    - MM
                    - MN
                    - MO
                    - MP
                    - MQ
                    - MR
                    - MS
                    - MT
                    - MU
                    - MV
                    - MW
                    - MX
                    - MY
                    - MZ
                    - NA
                    - NC
                    - NE
                    - NF
                    - NG
                    - NI
                    - NL
                    - NO
                    - NP
                    - NR
                    - NU
                    - NZ
                    - OM
                    - PA
                    - PE
                    - PF
                    - PG
                    - PH
                    - PK
                    - PL
                    - PM
                    - PN
                    - PR
                    - PS
                    - PT
                    - PW
                    - PY
                    - QA
                    - RE
                    - RO
                    - RS
                    - RU
                    - RW
                    - SA
                    - SB
                    - SC
                    - SD
                    - SE
                    - SG
                    - SH
                    - SI
                    - SJ
                    - SK
                    - SL
                    - SM
                    - SN
                    - SO
                    - SR
                    - SS
                    - ST
                    - SV
                    - SX
                    - SY
                    - SZ
                    - TC
                    - TD
                    - TF
                    - TG
                    - TH
                    - TJ
                    - TK
                    - TL
                    - TM
                    - TN
                    - TO
                    - TP
                    - TR
                    - TT
                    - TV
                    - TW
                    - TZ
                    - UA
                    - UG
                    - UK
                    - UM
                    - US
                    - UY
                    - UZ
                    - VA
                    - VC
                    - VE
                    - VG
                    - VI
                    - VN
                    - VU
                    - WF
                    - WS
                    - YE
                    - YT
                    - YU
                    - ZA
                    - ZM
                    - ZR
                    - ZW
                    type: string
                  latitude:
                    maxLength: 50
                    type: string
                  longitude:
                    maxLength: 50
                    type: string
                  postal_code:
                    maxLength: 10
                    type: string
                  state:
                    maxLength: 50
                    type: string
                type: object
              bank_account:
                $schema: http://json-schema.org/schema#
                additionalProperties: 0
                description: The bank details of a tenant can be provided for record
                  keeping purposes. Tenants cannot be paid using these bank details.
                properties:
                  account_name:
                    maxLength: 50
                    minLength: 1
                    type: string
                  account_number:
                    maxLength: 8
                    minLength: 3
                    pattern: ^\d+$
                    type: string
                  bank_name:
                    maxLength: 50
                    type: string
                  branch_code:
                    maxLength: 6
                    minLength: 6
                    pattern: ^\d+$
                    type: string
                  branch_name:
                    maxLength: 50
                    type: string
                type: object
              business_name:
                description: Required if account_type is business.
                maxLength: 50
                type: string
              comment:
                maxLength: 6000
                type: string
              customer_id:
                description: The customer ID is a unique, case-sensitive value per
                  API consumer. The value can be used to retrieve and update the entity.
                  Providing `null` on update will remove the customer ID associated
                  with the entity.<br><br>Please note that currently, this functionality
                  is marked as **experimental**; we strongly recommend keeping track
                  of PayProp entity `external_id` along with your `customer_id`.
                maxLength: 50
                minLength: 1
                pattern: ^[a-zA-Z0-9_-]+$
                type:
                - string
                - 'null'
              customer_reference:
                description: A unique value you can add to identify the tenant. This
                  is visible on the platform. This can be used to enhance the accuracy
                  of recommendations in the Unreconciled payments screen.
                maxLength: 50
                type: string
              date_of_birth:
                example: '1991-08-24'
                format: date
                type:
                - string
                - 'null'
              email_address:
                description: Specify the email address that you would like the tenant's
                  communications to go to. This includes invoices, arrears reminders,
                  arrears letters, rent receipts, and monthly statements if this is
                  activated by the agent. This also gives access to the tenant portal.
                format: email
                maxLength: 50
                type:
                - string
                - 'null'
              email_cc:
                description: Up to 10 email addresses can be listed here, recipients
                  will be copied on the tenant's communications but will not be given
                  access to the tenant portal.
                items:
                  format: email
                  type: string
                maxItems: 10
                type: array
              fax:
                maxLength: 15
                type: string
              first_name:
                description: Required if account_type is individual.
                maxLength: 50
                type: string
              has_bank_account:
                description: Required if updating bank account. Setting the field
                  to false will remove existing bank account details.
                type: boolean
              id:
                example: 'D8eJPwZG7j'
                maxLength: 32
                minLength: 10
                pattern: ^[a-zA-Z0-9]+$
                type: string
              id_number:
                description: Personal identification number from e.g. Passport/driving
                  license.
                maxLength: 15
                type: string
              id_type_id:
                description: 'ID Type external id. Ref: [/api/docs/agency?version=v1.1#operation/get_entity_id_types](/api/docs/agency?version=v1.1#operation/get_entity_id_types).'
                maxLength: 32
                type:
                - string
                - 'null'
              last_name:
                description: Required if account_type is individual.
                maxLength: 50
                type: string
              lead_days:
                description: "Tenant invoices can be sent ahead of their due date,
                  a `lead_days` value of 2 will send invoices 2 days in advance. Please
                  note this can in some cases lead to invoices not being sent and
                  will require a single frequency invoice to replace it. We recommend
                  following these rules:\n\t\t\t\t<ul>\n\t\t\t\t\t<li>If invoice date
                  minus `lead_days` is less than or equal to today, then send a single
                  invoice to replace the first invoice.</li>\n\t\t\t\t\t<li>Only update
                  the invoice start date if you want to skip one or more invoices,
                  otherwise just leave it blank.</li>\n\t\t\t\t\t<li>Then just account
                  for month ends, so if the on day is one and the lead days are 2,
                  you will end up with 30, or 29 etc.</li>\n\t\t\t\t</ul>"
                maximum: 31
                type: integer
              mobile_number:
                description: The mobile phone number you would like receive notifications
                  regarding this tenant.
                maxLength: 15
                type:
                - string
                - 'null'
              notify_email:
                type: boolean
              notify_sms:
                type: boolean
              phone:
                maxLength: 15
                type: string
              vat_number:
                maxLength: 50
                type: string
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create tenant
      tags:
      - Tenant Entity
  /entity/tenant/{external_id}:
    get:
      description: "Get tenant\n"
      operationId: get_tenant
      parameters:
      - description: External ID of tenant.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Retrieved tenant.
          schema: *18
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get tenant
      tags:
      - Tenant Entity
    put:
      consumes:
      - application/json
      description: "Update tenant\n"
      operationId: update_tenant
      parameters:
      - description: Tenant to update.
        in: body
        name: tenant
        schema:
          $schema: http://json-schema.org/schema#
          additionalProperties: 0
          allOf:
          - if:
              properties:
                account_type:
                  const: individual
            then:
              properties:
                first_name:
                  minLength: 2
                last_name:
                  minLength: 2
          - if:
              properties:
                account_type:
                  const: business
            then:
              properties:
                business_name:
                  minLength: 2
          - if:
              properties:
                has_bank_account:
                  const: 1
            then:
              properties:
                bank_account: {}
          - if:
              properties:
                mobile_number:
                  minLength: 1
            then:
              properties:
                mobile_number:
                  pattern: ^[1-9]\d+$
          properties:
            account_type:
              description: This determines how the tenant is addressed on their communications.
                Individuals are addressed by the `first_name` and `last_name` field,
                whereas a business is addressed by the `business_name` field.
              enum:
              - individual
              - business
              type: string
            address:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The address that will be used on tenant communications.
                Typically this is the rental address, but can be a guarantor.
              properties:
                address_line_1:
                  maxLength: 50
                  type: string
                address_line_2:
                  maxLength: 50
                  type: string
                address_line_3:
                  maxLength: 50
                  type: string
                city:
                  maxLength: 50
                  type: string
                country_code:
                  enum:
                  - AD
                  - AE
                  - AF
                  - AG
                  - AI
                  - AL
                  - AM
                  - AN
                  - AO
                  - AQ
                  - AR
                  - AS
                  - AT
                  - AU
                  - AW
                  - AX
                  - AZ
                  - BA
                  - BB
                  - BD
                  - BE
                  - BF
                  - BG
                  - BH
                  - BI
                  - BJ
                  - BL
                  - BM
                  - BN
                  - BO
                  - BQ
                  - BR
                  - BS
                  - BT
                  - BV
                  - BW
                  - BY
                  - BZ
                  - CA
                  - CC
                  - CD
                  - CF
                  - CG
                  - CH
                  - CI
                  - CK
                  - CL
                  - CM
                  - CN
                  - CO
                  - CR
                  - CU
                  - CV
                  - CW
                  - CX
                  - CY
                  - CZ
                  - DE
                  - DJ
                  - DK
                  - DM
                  - DO
                  - DZ
                  - EC
                  - EE
                  - EG
                  - EH
                  - ER
                  - ES
                  - ET
                  - FI
                  - FJ
                  - FK
                  - FM
                  - FO
                  - FR
                  - GA
                  - GD
                  - GE
                  - GF
                  - GG
                  - GH
                  - GI
                  - GL
                  - GM
                  - GN
                  - GP
                  - GQ
                  - GR
                  - GS
                  - GT
                  - GU
                  - GW
                  - GY
                  - HK
                  - HM
                  - HN
                  - HR
                  - HT
                  - HU
                  - ID
                  - IE
                  - IL
                  - IM
                  - IN
                  - IO
                  - IQ
                  - IR
                  - IS
                  - IT
                  - JE
                  - JM
                  - JO
                  - JP
                  - KE
                  - KG
                  - KH
                  - KI
                  - KM
                  - KN
                  - KP
                  - KR
                  - KW
                  - KY
                  - KZ
                  - LA
                  - LB
                  - LC
                  - LI
                  - LK
                  - LR
                  - LS
                  - LT
                  - LU
                  - LV
                  - LY
                  - MA
                  - MC
                  - MD
                  - ME
                  - MF
                  - MG
                  - MH
                  - MK
                  - ML
                  - MM
                  - MN
                  - MO
                  - MP
                  - MQ
                  - MR
                  - MS
                  - MT
                  - MU
                  - MV
                  - MW
                  - MX
                  - MY
                  - MZ
                  - NA
                  - NC
                  - NE
                  - NF
                  - NG
                  - NI
                  - NL
                  - NO
                  - NP
                  - NR
                  - NU
                  - NZ
                  - OM
                  - PA
                  - PE
                  - PF
                  - PG
                  - PH
                  - PK
                  - PL
                  - PM
                  - PN
                  - PR
                  - PS
                  - PT
                  - PW
                  - PY
                  - QA
                  - RE
                  - RO
                  - RS
                  - RU
                  - RW
                  - SA
                  - SB
                  - SC
                  - SD
                  - SE
                  - SG
                  - SH
                  - SI
                  - SJ
                  - SK
                  - SL
                  - SM
                  - SN
                  - SO
                  - SR
                  - SS
                  - ST
                  - SV
                  - SX
                  - SY
                  - SZ
                  - TC
                  - TD
                  - TF
                  - TG
                  - TH
                  - TJ
                  - TK
                  - TL
                  - TM
                  - TN
                  - TO
                  - TP
                  - TR
                  - TT
                  - TV
                  - TW
                  - TZ
                  - UA
                  - UG
                  - UK
                  - UM
                  - US
                  - UY
                  - UZ
                  - VA
                  - VC
                  - VE
                  - VG
                  - VI
                  - VN
                  - VU
                  - WF
                  - WS
                  - YE
                  - YT
                  - YU
                  - ZA
                  - ZM
                  - ZR
                  - ZW
                  type: string
                latitude:
                  maxLength: 50
                  type: string
                longitude:
                  maxLength: 50
                  type: string
                postal_code:
                  maxLength: 10
                  type: string
                state:
                  maxLength: 50
                  type: string
              type: object
            bank_account:
              $schema: http://json-schema.org/schema#
              additionalProperties: 0
              description: The bank details of a tenant can be provided for record
                keeping purposes. Tenants cannot be paid using these bank details.
              properties:
                account_name:
                  maxLength: 50
                  minLength: 1
                  type: string
                account_number:
                  maxLength: 8
                  minLength: 3
                  pattern: ^\d+$
                  type: string
                bank_name:
                  maxLength: 50
                  type: string
                branch_code:
                  maxLength: 6
                  minLength: 6
                  pattern: ^\d+$
                  type: string
                branch_name:
                  maxLength: 50
                  type: string
              type: object
            business_name:
              description: Required if account_type is business.
              maxLength: 50
              type: string
            comment:
              maxLength: 6000
              type: string
            customer_id:
              description: The customer ID is a unique, case-sensitive value per API
                consumer. The value can be used to retrieve and update the entity.
                Providing `null` on update will remove the customer ID associated
                with the entity.<br><br>Please note that currently, this functionality
                is marked as **experimental**; we strongly recommend keeping track
                of PayProp entity `external_id` along with your `customer_id`.
              maxLength: 50
              minLength: 1
              pattern: ^[a-zA-Z0-9_-]+$
              type:
              - string
              - 'null'
            customer_reference:
              description: A unique value you can add to identify the tenant. This
                is visible on the platform. This can be used to enhance the accuracy
                of recommendations in the Unreconciled payments screen.
              maxLength: 50
              type: string
            date_of_birth:
              example: '1991-08-24'
              format: date
              type:
              - string
              - 'null'
            email_address:
              description: Specify the email address that you would like the tenant's
                communications to go to. This includes invoices, arrears reminders,
                arrears letters, rent receipts, and monthly statements if this is
                activated by the agent. This also gives access to the tenant portal.
              format: email
              maxLength: 50
              type:
              - string
              - 'null'
            email_cc:
              description: Up to 10 email addresses can be listed here, recipients
                will be copied on the tenant's communications but will not be given
                access to the tenant portal.
              items:
                format: email
                type: string
              maxItems: 10
              type: array
            fax:
              maxLength: 15
              type: string
            first_name:
              description: Required if account_type is individual.
              maxLength: 50
              type: string
            has_bank_account:
              description: Required if updating bank account. Setting the field to
                false will remove existing bank account details.
              type: boolean
            id_number:
              description: Personal identification number from e.g. Passport/driving
                license.
              maxLength: 15
              type: string
            id_type_id:
              description: 'ID Type external id. Ref: [/api/docs/agency?version=v1.1#operation/get_entity_id_types](/api/docs/agency?version=v1.1#operation/get_entity_id_types).'
              maxLength: 32
              type:
              - string
              - 'null'
            last_name:
              description: Required if account_type is individual.
              maxLength: 50
              type: string
            lead_days:
              description: "Tenant invoices can be sent ahead of their due date, a
                `lead_days` value of 2 will send invoices 2 days in advance. Please
                note this can in some cases lead to invoices not being sent and will
                require a single frequency invoice to replace it. We recommend following
                these rules:\n\t\t\t\t<ul>\n\t\t\t\t\t<li>If invoice date minus `lead_days`
                is less than or equal to today, then send a single invoice to replace
                the first invoice.</li>\n\t\t\t\t\t<li>Only update the invoice start
                date if you want to skip one or more invoices, otherwise just leave
                it blank.</li>\n\t\t\t\t\t<li>Then just account for month ends, so
                if the on day is one and the lead days are 2, you will end up with
                30, or 29 etc.</li>\n\t\t\t\t</ul>"
              maximum: 31
              type: integer
            mobile_number:
              description: The mobile phone number you would like receive notifications
                regarding this tenant.
              maxLength: 15
              type:
              - string
              - 'null'
            notify_email:
              type: boolean
            notify_sms:
              type: boolean
            phone:
              maxLength: 15
              type: string
            vat_number:
              maxLength: 50
              type: string
          type: object
      - description: External ID of tenant.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Lookup entity based on given customer ID by overriding route
          `external_id`.
        in: query
        name: is_customer_id
        required: false
        type: boolean
      responses:
        '200':
          description: Updated tenant.
          schema: *18
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update tenant
      tags:
      - Tenant Entity
  /export/beneficiaries:
    get:
      description: |
        Return beneficiaries data export
      operationId: get_agency_beneficiaries_export
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: Return results from last modified time.
        in: query
        name: modified_from_time
        pattern: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}
        type: string
        x-example: '2018-02-22T17:01:59'
      - description: Timezone for modified_from_time.
        in: query
        name: modified_from_timezone
        type: string
        x-example: 'Europe/Zurich'
      - description: Return only Beneficiaries that are owners.
        in: query
        name: owners
        required: false
        type: boolean
      - description: External ID of beneficiary.
        in: query
        maxLength: 32
        name: external_id
        required: false
        type: string
      - description: Customer reference of beneficiary.
        in: query
        maxLength: 50
        name: customer_reference
        required: false
        type: string
      - description: To be used with `search_value`.
        enum:
        - business_name
        - first_name
        - last_name
        - email_address
        in: query
        name: search_by
        required: false
        type: string
      - description: To be used with `search_by`.
        in: query
        maxLength: 50
        minLength: 3
        name: search_value
        required: false
        type: string
      - description: Return only archived beneficiaries. Defaults to `false`.
        in: query
        name: is_archived
        required: false
        type: string
      - description: Lookup entities via `customer_id`.
        in: query
        maxLength: 50
        name: customer_id
        required: false
        type: string
      - description: Filter beneficiaries by bank account number.
        in: query
        maxLength: 32
        name: bank_account_number
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      - description: Filter beneficiaries by bank branch code.
        in: query
        maxLength: 32
        name: bank_branch_code
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      responses:
        '200':
          description: List of beneficiary item objects.
          schema:
            properties:
              items:
                description: List of ExportBeneficiaryItem objects.
                items:
                  $ref: api_definitions.yaml#/definitions/ExportBeneficiaryItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get beneficiaries data export
      tags:
      - Export
  /export/invoice-instructions:
    get:
      description: |
        Return invoice instructions data export
      operationId: get_invoice_instructions_export
      parameters:
      - description: Number of rows to return.
        in: query
        maximum: 25
        name: rows
        required: false
        type: integer
      - description: Page number to return.
        in: query
        name: page
        required: false
        type: integer
      - description: Filter invoice instructions based on tenant_id.
        in: query
        name: tenant_id
        required: false
        type: string
      - description: Filter invoice instructions based on property_id.
        in: query
        name: property_id
        required: false
        type: string
      - description: Filter invoice instructions based on invoice_rule_id.
        in: query
        name: invoice_rule_id
        required: false
        type: string
      - description: Filter invoice instructions based on external_id.
        in: query
        maxLength: 32
        name: external_id
        required: false
        type: string
      - description: Include processed invoice instructions.
        in: query
        name: include_processed
        required: false
        type: boolean
      - description: 'Invoice category ID. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency#operation/get_invoice_categories).'
        in: query
        name: category_id
        required: false
        type: string
      responses:
        '200':
          description: List of invoice instruction item objects.
          schema:
            properties:
              items:
                description: List of ExportInvoiceInstructionItem objects.
                items:
                  $ref: api_definitions.yaml#/definitions/ExportInvoiceInstructionItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get invoice instructions data export
      tags:
      - Export
  /export/invoices:
    get:
      description: "Return invoice data export\n"
      operationId: get_invoices_export
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: Return results from last modified time.
        in: query
        name: modified_from_time
        pattern: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}
        type: string
        x-example: '2018-02-22T17:01:59'
      - description: Timezone for modified_from_time.
        in: query
        name: modified_from_timezone
        type: string
        x-example: 'Europe/Zurich'
      - description: Filter invoices based on external ID.
        in: query
        maxLength: 32
        name: external_id
        required: false
        type: string
      - description: Filter invoices based on external property id.
        in: query
        maxLength: 32
        name: property_id
        required: false
        type: string
      - description: Filter invoices based on external tenant id.
        in: query
        maxLength: 32
        name: tenant_id
        required: false
        type: string
      - description: 'Invoice category ID. Ref: [/api/docs/agency?version=v1.1#operation/get_invoice_categories](/api/docs/agency#operation/get_invoice_categories).'
        in: query
        name: category_id
        required: false
        type: string
      responses:
        '200':
          description: List of invoice item objects.
          schema:
            properties:
              items:
                description: List of ExportInvoiceItem objects.
                items:
                  $ref: api_definitions.yaml#/definitions/ExportInvoiceItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get invoice data export
      tags:
      - Export
  /export/payments:
    get:
      description: "Return payment data export\n"
      operationId: get_payments_export
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: Return results from last modified time.
        in: query
        name: modified_from_time
        pattern: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}
        type: string
        x-example: '2018-02-22T17:01:59'
      - description: Timezone for modified_from_time.
        in: query
        name: modified_from_timezone
        type: string
        x-example: 'Europe/Zurich'
      - description: Filter payments based on external ID.
        in: query
        maxLength: 32
        name: external_id
        required: false
        type: string
      - description: Filter payments based on external `property_id`.
        in: query
        maxLength: 32
        name: property_id
        required: false
        type: string
      - description: Include payment target beneficiary info.
        in: query
        name: include_beneficiary_info
        required: false
        type: boolean
      - description: 'Payment category ID. Ref: [/api/docs/agency?version=v1.1#operation/get_payment_categories](/api/docs/agency#operation/get_payment_categories).'
        in: query
        name: category_id
        required: false
        type: string
      - description: Filter payments based on `maintenance_ticket_id`.
        in: query
        maxLength: 32
        name: maintenance_ticket_id
        required: false
        type: string
      responses:
        '200':
          description: List of payment item objects.
          schema:
            properties:
              items:
                description: List of ExportPaymentItem objects.
                items:
                  $ref: api_definitions.yaml#/definitions/ExportPaymentItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get payment data export
      tags:
      - Export
  /export/properties:
    get:
      description: "Return property data export\n"
      operationId: get_property_export
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: Return results from last modified time.
        in: query
        name: modified_from_time
        pattern: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}
        type: string
        x-example: '2018-02-22T17:01:59'
      - description: Timezone for `modified_from_time`.
        in: query
        name: modified_from_timezone
        type: string
        x-example: 'Europe/Zurich'
      - description: External ID of property.
        in: query
        maxLength: 32
        name: external_id
        required: false
        type: string
      - description: Customer reference of property.
        in: query
        maxLength: 50
        name: customer_reference
        required: false
        type: string
      - description: To be used with `search_value`.
        enum:
        - name
        - city
        - state
        - first_line
        - third_line
        - postal_code
        - second_line
        in: query
        name: search_by
        required: false
        type: string
      - description: To be used with `search_by`.
        in: query
        maxLength: 50
        minLength: 3
        name: search_value
        required: false
        type: string
      - description: Include last invoice, incoming payment and outgoing payment processing
          info.
        in: query
        name: include_last_processing_info
        required: false
        type: boolean
      - description: Include active tenancies.
        in: query
        name: include_active_tenancies
        required: false
        type: boolean
      - description: Include commission amount and percentage.
        in: query
        name: include_commission
        required: false
        type: boolean
      - description: Return only properties that have been archived. Defaults to `false`.
        in: query
        name: is_archived
        required: false
        type: string
      - description: Lookup entities based on `customer_id`.
        in: query
        maxLength: 50
        name: customer_id
        required: false
        type: string
      - description: Include contract amount.
        in: query
        name: include_contract_amount
        required: false
        type: boolean
      - description: Include balance amount.
        in: query
        name: include_balance
        required: false
        type: boolean
      responses:
        '200':
          description: List of property item objects.
          schema:
            properties:
              items:
                description: List of ExportPropertyItem objects.
                items:
                  $ref: api_definitions.yaml#/definitions/ExportPropertyItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get property data export
      tags:
      - Export
  /export/tenants:
    get:
      description: "Return tenant data export\n"
      operationId: get_tenants_export
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: Return results from last modified time.
        in: query
        name: modified_from_time
        pattern: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}
        type: string
        x-example: '2018-02-22T17:01:59'
      - description: Timezone for modified_from_time.
        in: query
        name: modified_from_timezone
        type: string
        x-example: 'Europe/Zurich'
      - description: External ID of tenant.
        in: query
        maxLength: 32
        name: external_id
        required: false
        type: string
      - description: Customer reference of tenant.
        in: query
        maxLength: 50
        name: customer_reference
        required: false
        type: string
      - collectionFormat: multi
        description: To be used with `search_value`.
        in: query
        items:
          enum:
          - business_name
          - first_name
          - last_name
          - email_address
          type: string
        name: search_by
        required: false
        type: array
      - description: To be used with `search_by`.
        in: query
        maxLength: 50
        minLength: 3
        name: search_value
        required: false
        type: string
      - description: Return only tenants that have been archived. Defaults to `false`.
        in: query
        name: is_archived
        required: false
        type: string
      - description: Lookup entities based on `customer_id`.
        in: query
        maxLength: 50
        name: customer_id
        required: false
        type: string
      - description: Filter tenants on property relationships.
        in: query
        maxLength: 32
        minLength: 10
        name: property_id
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      responses:
        '200':
          description: List of tenant item objects.
          schema:
            properties:
              items:
                description: List of ExportTenantItem objects.
                items:
                  $ref: api_definitions.yaml#/definitions/ExportTenantItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get tenant data export
      tags:
      - Export
  /global-beneficiaries:
    get:
      description: "Return global beneficiaries\n"
      operationId: get_global_beneficiaries
      responses:
        '200':
          description: Global beneficiaries.
          schema:
            $ref: api_definitions.yaml#/definitions/GlobalBeneficiaries
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Global beneficiaries
      tags:
      - Global Beneficiaries
  /id-types:
    get:
      description: "Return available ID types.\n"
      operationId: get_entity_id_types
      responses:
        '200':
          description: Available ID types.
          schema:
            $ref: api_definitions.yaml#/definitions/IDTypes
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get available ID types
      tags:
      - ID types
  /invoices/categories:
    get:
      description: "Return invoice categories\n"
      operationId: get_invoice_categories
      parameters:
      - description: External ID of category.
        in: query
        maxLength: 32
        name: category_id
        required: false
        type: string
      - description: Include inactive categories.
        in: query
        name: include_inactive
        required: false
        type: boolean
      responses:
        '200':
          description: Invoice categories.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionCategories
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Invoice categories
      tags:
      - Invoices
    post:
      consumes:
      - application/json
      description: "Create new invoice category\n"
      operationId: create_invoice_category
      parameters:
      - description: Invoice category to create.
        in: body
        name: invoice category
        schema:
          $ref: api_definitions.yaml#/definitions/InvoiceCategory
      responses:
        '200':
          description: Created invoice category.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionCategoriesItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create invoice category
      tags:
      - Invoices
  /invoices/categories/{external_id}:
    put:
      consumes:
      - application/json
      description: "Update invoice category\n"
      operationId: update_invoice_category
      parameters:
      - description: Invoice category to update.
        in: body
        name: invoice category
        schema:
          $ref: api_definitions.yaml#/definitions/InvoiceCategory
      - description: External ID of category.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      responses:
        '200':
          description: Updated invoice category.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionCategoriesItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update invoice category
      tags:
      - Invoices
  /maintenance/categories:
    get:
      description: |
        Get maintenance ticket categories
      operationId: get_maintenance_ticket_categories
      responses:
        '200':
          description: Maintenance ticket categories.
          schema:
            properties:
              categories:
                description: Maintenance ticket category item structure.
                items:
                  $ref: api_definitions.yaml#/definitions/MaintenanceTicketCategoryItem
                type: array
              count:
                description: Number of maintenance ticket categories.
                example: '12'
                type: number
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get maintenance ticket categories
      tags:
      - Maintenance
  /maintenance/tickets:
    get:
      description: "List maintenance tickets\n"
      operationId: get_maintenance_tickets
      parameters:
      - description: Restrict rows returned.
        in: query
        minimum: 1
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        minimum: 1
        name: page
        required: false
        type: integer
      - description: Filter tickets from given date (e.g. 2020-01-01)
        in: query
        name: from_date
        pattern: \d{4}-\d{2}-\d{2}
        required: false
        type: string
      - description: Filter tickets to given date (e.g. 2020-01-31)
        in: query
        name: to_date
        pattern: \d{4}-\d{2}-\d{2}
        required: false
        type: string
      - description: Ticket category external ID.
        in: query
        maxLength: 32
        minLength: 10
        name: category_id
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      - description: Ticket status ID.
        enum:
        - new
        - in_progress
        - on_hold
        - rejected
        - resolved
        in: query
        name: status_id
        required: false
        type: string
      - description: Filter tickets on property external ID.
        in: query
        maxLength: 32
        minLength: 10
        name: property_id
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      - description: Filter tickets on tenant external ID.
        in: query
        maxLength: 32
        minLength: 10
        name: tenant_id
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      - description: Filter tickets on emergency status.
        in: query
        name: is_emergency
        required: false
        type: boolean
      responses:
        '200':
          description: Maintenance tickets.
          schema:
            properties:
              count:
                description: Number of maintenance tickets.
                example: '12'
                type: number
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
              tickets:
                description: Maintenance ticket item structure.
                items:
                  $ref: api_definitions.yaml#/definitions/MaintenanceTicketItem
                type: array
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: List maintenance tickets
      tags:
      - Maintenance
    post:
      description: "Create maintenance tickets\n"
      operationId: create_maintenance_tickets
      parameters:
      - description: Maintenance ticket to create.
        in: body
        name: maintenance ticket
        schema:
          $ref: api_definitions.yaml#/definitions/MaintenanceTicketCreateBody
      responses:
        '200':
          description: Created maintenance ticket.
          schema:
            $ref: api_definitions.yaml#/definitions/MaintenanceTicketItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create maintenance tickets
      tags:
      - Maintenance
  /maintenance/tickets/{external_id}:
    get:
      description: "Get maintenance ticket\n"
      operationId: get_maintenance_ticket
      parameters:
      - description: External ID of maintenance ticket.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Maintenance ticket.
          schema:
            $ref: api_definitions.yaml#/definitions/MaintenanceTicketItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get maintenance ticket
      tags:
      - Maintenance
    put:
      description: "Update maintenance tickets\n"
      operationId: update_maintenance_tickets
      parameters:
      - description: Maintenance ticket to update.
        in: body
        name: maintenance ticket
        schema:
          $ref: api_definitions.yaml#/definitions/MaintenanceTicketUpdateBody
      responses:
        '200':
          description: Updated maintenance ticket.
          schema:
            $ref: api_definitions.yaml#/definitions/MaintenanceTicketItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update maintenance tickets
      tags:
      - Maintenance
  /maintenance/tickets/{ticket_id}/messages:
    get:
      description: |
        Get maintenance ticket messages
      operationId: get_maintenance_ticket_messages
      parameters:
      - description: Maintenance ticket external ID.
        in: path
        maxLength: 32
        minLength: 10
        name: ticket_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Restrict rows returned.
        in: query
        minimum: 1
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        minimum: 1
        name: page
        required: false
        type: integer
      - description: Filter ticket messages from given date (e.g. 2020-01-01)
        in: query
        name: from_date
        pattern: \d{4}-\d{2}-\d{2}
        required: false
        type: string
      - description: Filter ticket messages to given date (e.g. 2020-01-31)
        in: query
        name: to_date
        pattern: \d{4}-\d{2}-\d{2}
        required: false
        type: string
      - description: Ticket message author type.
        enum:
        - agency
        - tenant
        in: query
        name: author_type
        required: false
        type: string
      - description: Filter ticket messages on privacy status.
        in: query
        name: is_private
        required: false
        type: boolean
      responses:
        '200':
          description: Maintenance ticket messages.
          schema:
            properties:
              count:
                description: Number of maintenance ticket messages.
                example: '12'
                type: number
              messages:
                description: Maintenance ticket message item structure.
                items:
                  $ref: api_definitions.yaml#/definitions/MaintenanceTicketMessageItem
                type: array
              pagination:
                $ref: api_definitions.yaml#/definitions/Pagination
                type: object
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get maintenance ticket messages
      tags:
      - Maintenance
    post:
      description: |
        Create maintenance ticket messages
      operationId: create_maintenance_ticket_messages
      parameters:
      - description: Maintenance ticket external ID.
        in: path
        maxLength: 32
        minLength: 10
        name: ticket_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Maintenance ticket message to create.
        in: body
        name: maintenance ticket message
        schema:
          $ref: api_definitions.yaml#/definitions/MaintenanceTicketMessageCreateBody
      responses:
        '200':
          description: Created maintenance ticket message.
          schema:
            $ref: api_definitions.yaml#/definitions/MaintenanceTicketMessageItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create maintenance ticket messages
      tags:
      - Maintenance
  /maintenance/tickets/{ticket_id}/messages/{message_id}:
    get:
      description: |
        Get maintenance ticket message
      operationId: get_maintenance_ticket_message
      parameters:
      - description: Maintenance ticket external ID.
        in: path
        maxLength: 32
        minLength: 10
        name: ticket_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Maintenance ticket message external ID.
        in: path
        maxLength: 32
        minLength: 10
        name: message_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Maintenance ticket message.
          schema:
            $ref: api_definitions.yaml#/definitions/MaintenanceTicketMessageItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get maintenance ticket message
      tags:
      - Maintenance
  /meta/me:
    get:
      description: "Return token meta information\n"
      operationId: get_token_meta_information
      responses:
        '200':
          description: Token meta information.
          schema:
            properties:
              agency:
                description: Agency meta information.
                properties:
                  id:
                    description: External ID of agency.
                    maxLength: 32
                    type: string
                  logo:
                    description: Absolute URL to agency logo.
                    type:
                    - string
                    - 'null'
                  name:
                    description: Agency name.
                    type: string
                type: object
              scopes:
                description: List of token scopes.
                items:
                  type: string
                type: array
              user:
                description: Token user meta information.
                properties:
                  email:
                    description: Email address of token user.
                    type: string
                  full_name:
                    description: Full name of token user.
                    type: string
                  id:
                    description: External ID of token user.
                    maxLength: 32
                    type: string
                  is_admin:
                    description: Is token user main administrator.
                    type: boolean
                  type:
                    description: Token user type.
                    enum:
                    - agent
                    - agency
                    type: string
                type: object
            type: object
      summary: Get token meta information
      tags:
      - Meta
  /payments/categories:
    get:
      description: "Return payment categories\n"
      operationId: get_payment_categories
      parameters:
      - description: External ID of category.
        in: query
        maxLength: 32
        name: category_id
        required: false
        type: string
      - description: Include inactive categories.
        in: query
        name: include_inactive
        required: false
        type: boolean
      responses:
        '200':
          description: Payment categories.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionCategories
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Payment categories
      tags:
      - Payments
    post:
      consumes:
      - application/json
      description: "Create new payment category\n"
      operationId: create_payment_category
      parameters:
      - description: Payment category to create.
        in: body
        name: payment category
        schema:
          $ref: api_definitions.yaml#/definitions/InvoiceCategory
      responses:
        '200':
          description: Created payment category.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionCategoriesItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create payment category
      tags:
      - Payments
  /payments/categories/{external_id}:
    put:
      consumes:
      - application/json
      description: "Update payment category\n"
      operationId: update_payment_category
      parameters:
      - description: Payment category to update.
        in: body
        name: payment category
        schema:
          $ref: api_definitions.yaml#/definitions/InvoiceCategory
      - description: External ID of category.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      responses:
        '200':
          description: Updated payment category.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionCategoriesItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update payment category
      tags:
      - Payments
  /posted-payments:
    post:
      consumes:
      - application/json
      description: |
        Allows capturing incoming payments that still need to reach the bank.
      operationId: create_posted_payment
      parameters:
      - description: Posted payment to create.
        in: body
        name: posted payment
        schema:
          $ref: api_definitions.yaml#/definitions/PostedPayment
      responses:
        '200':
          description: Created posted payment.
          schema:
            $ref: api_definitions.yaml#/definitions/PostedPayment
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create posted payment
      tags:
      - Posted payments
  /posted-payments/types:
    get:
      description: "Return posted payments types\n"
      operationId: get_posted_payments_types
      responses:
        '200':
          description: Posted payments types.
          schema:
            $ref: api_definitions.yaml#/definitions/PostedPaymentTypes
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Get posted payments types
      tags:
      - Posted payments
  /reminders/{entity}/{external_id}:
    get:
      description: "Return entity reminders\n"
      operationId: get_entity_reminders
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: External ID of reminder.
        in: query
        maxLength: 32
        name: reminder_id
        required: false
        type: string
      - collectionFormat: csv
        description: |
          List of reminder statuses e.g. `?statuses=active,complete`. Returns `active` reminders by default.
        in: query
        items:
          enum:
          - active
          - complete
          type: string
        name: statuses
        required: false
        type: array
      - description: External ID of entity.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Entity type to set reminder on.
        enum:
        - tenant
        - property
        - beneficiary
        in: path
        name: entity
        required: true
        type: string
      responses:
        '200':
          description: Entity reminders.
          schema:
            $ref: api_definitions.yaml#/definitions/EntityReminders
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Entity reminders
      tags:
      - Entity Reminders
    post:
      consumes:
      - application/json
      description: "Create new entity reminder.\n"
      operationId: create_entity_reminder
      parameters:
      - description: Entity reminder to create.
        in: body
        name: entity reminder
        schema:
          $ref: api_definitions.yaml#/definitions/Reminder
      - description: External ID of entity.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: Entity type to set reminder on.
        enum:
        - tenant
        - property
        - beneficiary
        in: path
        name: entity
        required: true
        type: string
      responses:
        '200':
          description: Created entity reminder.
          schema:
            $ref: api_definitions.yaml#/definitions/ReminderItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create entity reminder
      tags:
      - Entity Reminders
  /reminders/{entity}/{external_id}/{reminder_external_id}:
    put:
      consumes:
      - application/json
      description: "Update entity reminder.\n"
      operationId: update_entity_reminder
      parameters:
      - description: Entity reminder to update.
        in: body
        name: entity reminder
        schema:
          $ref: api_definitions.yaml#/definitions/Reminder
      - description: External ID of entity.
        in: path
        maxLength: 32
        name: external_id
        required: true
        type: string
      - description: External ID of reminder.
        in: path
        maxLength: 32
        name: reminder_external_id
        required: true
        type: string
      - description: Entity type to set reminder on.
        enum:
        - tenant
        - property
        - beneficiary
        in: path
        name: entity
        required: true
        type: string
      responses:
        '200':
          description: Updated entity reminder.
          schema:
            $ref: api_definitions.yaml#/definitions/ReminderItem
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update entity reminder
      tags:
      - Entity Reminders
  /report/agency/income:
    get:
      description: "Return agency income report\n"
      operationId: get_agency_income_report
      parameters:
      - description: Show agency income report summary for the given year. Must be
          used with `month` parameter. Required if `on_date` is not provided.
        in: query
        name: year
        pattern: \d{4}
        required: false
        type: string
        x-example: '2022'
      - description: Show agency income report summary for the given month. Must be
          used with `year` parameter. Required if `on_date` is not provided.
        in: query
        name: month
        pattern: \d{2}
        required: false
        type: string
        x-example: '08'
      - description: Show detailed agency income report for the given date. Take precedence
          over `month` and `year` parameters. Required if `year` and `month` are not
          provided.
        format: date
        in: query
        name: on_date
        required: false
        type: string
      responses:
        '200':
          description: Agency income report.
          schema:
            $ref: api_definitions.yaml#/definitions/AgencyIncomeReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Agency income
      tags:
      - Reports
  /report/all-payments:
    get:
      description: "Return all payments\n"
      operationId: get_all_payments
      parameters:
      - description: External ID of tenant.
        in: query
        maxLength: 32
        name: tenant_id
        required: false
        type: string
      - description: External ID of property.
        in: query
        maxLength: 32
        name: property_id
        required: false
        type: string
      - description: External ID of category.
        in: query
        maxLength: 32
        name: category_id
        required: false
        type: string
      - description: Show report from given date (e.g. 2020-01-01)
        format: date
        in: query
        name: from_date
        required: false
        type: string
      - description: Show report to given date (e.g. 2020-01-31)
        format: date
        in: query
        name: to_date
        required: false
        type: string
      - description: Filter report based on given date fields. Defaults to `reconciliation_date`.
        enum:
        - remittance_date
        - reconciliation_date
        in: query
        name: filter_by
        required: false
        type: string
      - description: External ID of incoming payment.
        in: query
        maxLength: 32
        name: incoming_transaction_id
        required: false
        type: string
      - description: External ID of parent payment.
        in: query
        maxLength: 32
        name: parent_payment_id
        required: false
        type: string
      - description: External ID of batched payment.
        in: query
        maxLength: 32
        name: payment_batch_id
        required: false
        type: string
      responses:
        '200':
          description: All payments.
          schema:
            $ref: api_definitions.yaml#/definitions/AllPaymentsReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: All payments
      tags:
      - Reports
  /report/beneficiary/balances:
    get:
      description: |
        Return beneficiaries with balances report
      operationId: get_beneficiary_balances
      parameters:
      - description: Restrict rows returned.
        in: query
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        name: page
        required: false
        type: integer
      - description: External ID of property.
        in: query
        maxLength: 32
        name: property_id
        required: false
        type: string
      - description: External ID of category.
        in: query
        maxLength: 32
        name: category_id
        required: false
        type: string
      - description: Show beneficiary balances below given amount. Can be used with
          `balance_above`.
        in: query
        name: balance_below
        required: false
        type: string
      - description: Show beneficiary balances above given amount. Can be used with
          `balance_below`.
        in: query
        name: balance_above
        required: false
        type: string
      - description: External ID of beneficiary. Relevant where `beneficiary_type`
          is `beneficiary` or `global_beneficiary`.
        in: query
        maxLength: 32
        name: beneficiary_id
        required: false
        type: string
      - description: Beneficiary type.
        enum:
        - agency
        - beneficiary
        - deposit_account
        - property_account
        - global_beneficiary
        in: query
        name: beneficiary_type
        required: false
        type: string
      - description: Name of global beneficiary e.g. "City of Johannesburg". If provided,
          takes precedence over `beneficiary_type`.
        in: query
        maxLength: 50
        name: global_beneficiary_name
        required: false
        type: string
      responses:
        '200':
          description: Beneficiary balances report.
          schema:
            $ref: api_definitions.yaml#/definitions/BeneficiaryBalancesReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Beneficiaries with balances
      tags:
      - Reports
  /report/icdn:
    get:
      description: |
        Return invoice, credit and debit notes (ICDN)
      operationId: get_invoice_credit_debit_notes
      parameters:
      - description: External ID of tenant.
        in: query
        maxLength: 32
        name: tenant_id
        required: false
        type: string
      - description: External ID of category.
        in: query
        maxLength: 32
        name: category_id
        required: false
        type: string
      - description: External ID of property.
        in: query
        maxLength: 32
        name: property_id
        required: false
        type: string
      - description: Show report for specific transaction type.
        enum:
        - invoice
        - debit_note
        - credit_note
        in: query
        name: type
        required: false
        type: string
      - description: Show report from given date (e.g. 2020-01-01)
        format: date
        in: query
        name: from_date
        required: false
        type: string
      - description: Show report to given date (e.g. 2020-01-31)
        format: date
        in: query
        name: to_date
        required: false
        type: string
      responses:
        '200':
          description: ICDN report.
          schema:
            $ref: api_definitions.yaml#/definitions/ICDNReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: ICDN
      tags:
      - Reports
  /report/processing-summary:
    get:
      description: |
        Return agency processing summary. The report is cached for an hour.
      operationId: get_processing_summary_report
      parameters:
      - description: Show report from given date (e.g. 2020-01-01). Defaults to today.
        format: date
        in: query
        name: from_date
        required: false
        type: string
      - description: Show report to given date (e.g. 2020-01-31). Defaults to today.
        format: date
        in: query
        name: to_date
        required: false
        type: string
      responses:
        '200':
          description: Processing summary report.
          schema:
            $ref: api_definitions.yaml#/definitions/ProcessingSummaryReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Processing summary
      tags:
      - Reports
  /report/tenant/balances:
    get:
      description: "Return tenant balances report\n"
      operationId: get_tenant_balances
      parameters:
      - description: External ID of property.
        in: query
        maxLength: 32
        name: property_id
        required: false
        type: string
      - description: External ID of tenant.
        in: query
        maxLength: 32
        name: tenant_id
        required: false
        type: string
      - description: Tenant balances on given date. Defaults to today.
        format: date
        in: query
        name: on_date
        required: false
        type: string
      - description: Show tenant balances below given amount. Can be used with `balance_above`.
        in: query
        name: balance_below
        required: false
        type: string
      - description: Show tenant balances above given amount. Can be used with `balance_below`.
        in: query
        name: balance_above
        required: false
        type: string
      responses:
        '200':
          description: Tenant balances report.
          schema:
            $ref: api_definitions.yaml#/definitions/TenantBalancesReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Tenant balances
      tags:
      - Reports
  /report/tenant/statement:
    get:
      description: "Return tenant statement\n"
      operationId: get_tenant_statement
      parameters:
      - description: External ID of property.
        in: query
        maxLength: 32
        name: property_id
        required: true
        type: string
      - description: External ID of tenant.
        in: query
        maxLength: 32
        name: tenant_id
        required: true
        type: string
      - description: Show report from given date (e.g. 2020-01-01)
        format: date
        in: query
        name: from_date
        required: false
        type: string
      - description: Show report to given date (e.g. 2020-01-31)
        format: date
        in: query
        name: to_date
        required: false
        type: string
      responses:
        '200':
          description: Tenant statement.
          schema:
            $ref: api_definitions.yaml#/definitions/TenantStatementReport
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Tenant statement
      tags:
      - Reports
  /tags:
    get:
      description: "Return tags\n"
      operationId: get_tags
      parameters:
      - description: Number of rows to return.
        in: query
        maximum: 25
        minimum: 1
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        minimum: 1
        name: page
        required: false
        type: integer
      - description: External ID of tag.
        in: query
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        type: string
      - description: Filter tags by name.
        in: query
        maxLength: 32
        minLength: 1
        name: name
        pattern: ^[a-zA-Z0-9_\-\s]+$
        required: false
        type: string
      - description: Return tags with one or more links to the entity type.
        enum:
        - tenant
        - property
        - beneficiary
        in: query
        name: entity_type
        required: false
        type: string
      - description: External ID of entity. To be used with `entity_type`.
        in: query
        maxLength: 32
        minLength: 10
        name: entity_id
        pattern: ^[a-zA-Z0-9]+$
        required: false
        type: string
      responses:
        '200':
          description: Tags.
          schema:
            $ref: api_definitions.yaml#/definitions/TagsResponse
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Tags
      tags:
      - Tags
    post:
      consumes:
      - application/json
      description: |
        Create a new tag. If a tag with given name already exists, the existing tag will be returned instead.
      operationId: create_tag
      parameters:
      - description: Tag to create.
        in: body
        name: tag
        schema:
          properties:
            name:
              $ref: api_definitions.yaml#/definitions/TagName
          required:
          - name
          type: object
      responses:
        '200':
          description: Created tag.
          schema:
            $ref: api_definitions.yaml#/definitions/Tag
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create tag
      tags:
      - Tags
  /tags/entities/{entity_type}/{entity_id}:
    post:
      description: |
        Link one to many tags with an entity
      operationId: link_tags_with_entity
      parameters:
      - description: External ID of entity.
        in: path
        maxLength: 32
        minLength: 10
        name: entity_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Entity type.
        enum:
        - tenant
        - property
        - beneficiary
        in: path
        name: entity_type
        required: true
        type: string
      - description: Tag names or external IDs to be linked with entity.
        in: body
        name: tags
        schema:
          $ref: api_definitions.yaml#/definitions/LinkTagsBody
      responses:
        '200':
          description: List of linked tags.
          schema:
            properties:
              items:
                items:
                  $ref: api_definitions.yaml#/definitions/Tag
                type: array
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Link tags with entity
      tags:
      - Tags
  /tags/{external_id}:
    delete:
      description: "Delete tag\n"
      operationId: delete_tag
      parameters:
      - description: External ID of tag.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Success message.
          schema:
            properties:
              message:
                example: 'Tag has been successfully deleted.'
                type: string
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Delete tag
      tags:
      - Tags
    put:
      description: |
        Update tag. If a tag with the given name already exists, it will be merged into the target tag.
      operationId: update_tag
      parameters:
      - description: External ID of tag.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Tag to update.
        in: body
        name: tag
        schema:
          properties:
            name:
              $ref: api_definitions.yaml#/definitions/TagName
          required:
          - name
          type: object
      responses:
        '200':
          description: Tag.
          schema:
            $ref: api_definitions.yaml#/definitions/Tag
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update tag
      tags:
      - Tags
  /tags/{external_id}/entities:
    delete:
      description: "Delete tag entity link\n"
      operationId: delete_tag_entity_link
      parameters:
      - description: External ID of tag.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Entity type.
        enum:
        - tenant
        - property
        - beneficiary
        in: query
        name: entity_type
        required: true
        type: string
      - description: External ID of entity.
        in: query
        maxLength: 32
        minLength: 10
        name: entity_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      responses:
        '200':
          description: Success message.
          schema:
            properties:
              message:
                example: 'Tag link successfully removed from entity.'
                type: string
            type: object
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Delete tag entity link
      tags:
      - Tags
    get:
      description: "Return tagged entities\n"
      operationId: get_tagged_entities
      parameters:
      - description: External ID of tag.
        in: path
        maxLength: 32
        minLength: 10
        name: external_id
        pattern: ^[a-zA-Z0-9]+$
        required: true
        type: string
      - description: Filter tagged entities by type.
        enum:
        - tenant
        - property
        - beneficiary
        in: query
        name: entity_type
        required: false
        type: string
      - enum:
        - type
        - name
        in: query
        name: sort_by
        required: false
        type: string
      - enum:
        - asc
        - desc
        in: query
        name: sort_direction
        required: false
        type: string
      responses:
        '200':
          description: Tagged entities.
          schema:
            $ref: api_definitions.yaml#/definitions/TaggedEntitiesResponse
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Tagged entities
      tags:
      - Tags
  /transactions/credit-note:
    post:
      consumes:
      - application/json
      description: |
        Decreases the amount of money a tenant owes
      operationId: create_credit_note
      parameters:
      - description: Credit note to create.
        in: body
        name: credit note
        schema:
          $ref: api_definitions.yaml#/definitions/DebitCreditNoteRef
      responses:
        '200':
          description: Created credit note.
          schema:
            $ref: api_definitions.yaml#/definitions/DebitCreditNoteResponseRef
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create credit note
      tags:
      - Transactions
  /transactions/debit-note:
    post:
      consumes:
      - application/json
      description: |
        Increases the amount of money a tenant owes
      operationId: create_debit_note
      parameters:
      - description: Debit note to create.
        in: body
        name: debit note
        schema:
          $ref: api_definitions.yaml#/definitions/DebitCreditNoteRef
      responses:
        '200':
          description: Created debit note.
          schema:
            $ref: api_definitions.yaml#/definitions/DebitCreditNoteResponseRef
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Create debit note
      tags:
      - Transactions
  /transactions/frequencies:
    get:
      description: |
        Return transaction frequencies
      operationId: get_transaction_frequencies
      responses:
        '200':
          description: Transaction frequencies.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionFrequencies
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Transaction frequencies
      tags:
      - Transactions
  /transactions/tax:
    get:
      description: "Return transaction taxes\n"
      operationId: get_transactions_tax
      responses:
        '200':
          description: Transaction taxes.
          schema:
            $ref: api_definitions.yaml#/definitions/TransactionTaxes
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Transaction taxes
      tags:
      - Transactions
  /webhooks:
    get:
      description: "Return webhooks\n"
      operationId: get_webhooks
      responses:
        '200':
          description: Webhooks.
          schema:
            $ref: api_definitions.yaml#/definitions/Webhook
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Webhooks
      tags:
      - Webhooks
    put:
      consumes:
      - application/json
      description: "Update webhook.\n"
      operationId: update_webhook
      parameters:
      - description: Webhook to update.
        in: body
        name: webhook
        schema:
          $ref: api_definitions.yaml#/definitions/Webhook
      responses:
        '200':
          description: Updated webhook.
          schema:
            $ref: api_definitions.yaml#/definitions/Webhook
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Update webhooks
      tags:
      - Webhooks
  /webhooks/failures:
    get:
      description: "Return webhook failures\n"
      operationId: get_webhook_failures
      parameters:
      - description: Restrict rows returned.
        in: query
        minimum: 1
        name: rows
        required: false
        type: integer
      - description: Return given page number.
        in: query
        minimum: 1
        name: page
        required: false
        type: integer
      - description: |
          Show webhook failures from given date (e.g. `2023-10-14`). Defaults to today or `to_date`, if set.
        format: date
        in: query
        name: from_date
        required: false
        type: string
      - description: |
          Show webhook failures to given date (e.g. `2023-10-25`). Defaults to today or `from_date`, if set.
        format: date
        in: query
        name: to_date
        required: false
        type: string
      responses:
        '200':
          description: Webhook failures.
          schema:
            $ref: api_definitions.yaml#/definitions/WebhookFailures
        '400':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error400
        '401':
          description: Bad request errors.
          schema:
            $ref: api_definitions.yaml#/definitions/Error401
        '403':
          description: Invalid privileges error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error403
        '404':
          description: Not found error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error404
        '500':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error500
        '501':
          description: Not implemented error.
          schema:
            $ref: api_definitions.yaml#/definitions/Error501
      summary: Webhook failures
      tags:
      - Webhooks
produces:
- application/json
schemes:
- https
swagger: '2.0'
tags:
- description: |
    Please refer to the [attachments endpoint](/api/docs/agency?version=v1.1#tag/Attachments) to attach images and files to maintenance tickets and messages.
  name: Maintenance

```