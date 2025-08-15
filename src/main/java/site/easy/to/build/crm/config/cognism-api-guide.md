# Cognism API Guide

## Overview

The Cognism API is used to search, preview and enrich Contacts and Accounts. Cognism database provides extensive B2B contacts and sales intelligence data.

**Note:** API responses may differ from the results retrieved via Prospector search (Web Application). Cognism strives to ensure that the API is updated regularly to align as closely as possible with Prospector search results.

## Key Benefits

- Better accuracy on technology and event searches
- Ability to create more complex queries
- Unlimited results (removes limitation of 10,000 displayed results)
- Visibility for all open positions for each contact - with different emails and company phones
- Ability to configure the response fields in line with the entitlements

## Authentication

Cognism uses API keys to allow access to the APIs. The API Key is assigned to you by Cognism and it is used to authenticate and authorize each request.

- Your API key should be kept private, and should never be displayed publicly
- API keys have a time-to-live (TTL) of 6 months
- You can manually generate additional keys or remove existing ones using Cognism Prospector or Cognism Refresh

You can authenticate HTTP requests in one of two ways:

1. Using header field `"Authorization: Bearer <API KEY>"` (recommended)
2. Using query string `"api_key={API KEY}"`

## API Structure

API contains a set of HTTPS endpoints providing all necessary details to preview and redeem contacts of your choice. The API is built using RESTful endpoints and standard HTTP verbs.

## Entitlements

Entitlements are a defined set of Contact and Account data that is available for you. They are set by Cognism Support Team. Entitlement definition affects Search, Preview and Result. Entitlements are related to the API Key.

**Important:** Cognism API cannot be used unless Entitlements are properly set up.

## Basic Info

No matter what entitlement you choose, basic info will always be present. This is minimum data defining Contact or Account entity. Entitlements are built on top of basic info.

### Basic Contact Info

```json
{
  "id": "String (UUID)",
  "fullName": "String",
  "firstName": "String",
  "lastName": "String",
  "jobTitle": "String",
  "account": {
    "id": "String",
    "name": "String"
  }
}
```

### Basic Account Info

```json
{
  "id": "String",
  "name": "String"
}
```

## Rate Limiting

All API requests are subject to rate limits that exist independently of your API key's monthly usage allowance. The maximum rate limit is 1,000 requests per minute.

Number of previews available = Number of credits available, multiplied by 10.

### HTTP Headers and Response Codes

Use the HTTP headers to understand where the application is at for a given rate limit:

- `x-rate-total-limit`: The rate limit ceiling for all endpoints for the 1 minute window
- `x-rate-total-limit-remaining`: The number of requests left remaining for the 1 minute window
- `x-rate-limit-reset`: Number of seconds before rate limits are reset
- `x-rate-limit-user`: User name
- `x-rate-endpoint-limit`: The rate limit ceiling for single endpoints for the 1 minute window
- `x-rate-endpoint-limit-remaining`: The number of requests left remaining for single endpoints for the 1 minute window
- `x-rate-result-limit`: Total number of preview profiles within API contract (credits × 10)
- `x-rate-result-limit-remaining`: Total number of remaining requests within API contract
- `x-rate-result-limit-reset`: Number of seconds before rate result limit is reset (until end of current contract)

**HTTP 429 "Too Many Requests"**: This response code is returned when an application exceeds the rate limit for a given API endpoint, with the error: `{ "errors": [ { "code": 88, "message": "Rate limit exceeded" } ] }`

## Main API Endpoints

### Search API

Use this API to search Cognism Contacts and Accounts dataset by different parameters. The search and response models are dependant on defined Entitlement.

- Search results contain records that match all input search criteria
- Each Array field can have up to 1000 search terms in a single request

#### Search Contacts

```
POST https://app.cognism.com/api/search/contact/search?indexSize=25&lastReturnedKey=
```

Common search parameters:
- `ids`: Array[String] - Contact ids
- `fullName`: String - Contact name
- `firstName`: String - Contact first name
- `lastName`: String - Contact last name
- `jobTitles`: Array[String] - Contact current position job titles
- `excludeJobTitles`: Array[String] - Contact current position excluded job titles
- `seniority`: Array[String] - Contact current position seniority (Manager, Director, Partner, CXO, Owner, VP)

#### Search Accounts

```
POST https://app.cognism.com/api/search/account/search?indexSize=100&lastReturnedKey=
```

Common search parameters:
- `ids`: Array[String] - Account Cognism unique id
- `names`: Array[String] - Account names
- `excludeNames`: Array[String] - Account exclude names
- `domains`: Array[String] - Account domains
- `excludeDomains`: Array[String] - Account exclude domains
- `websites`: Array[String] - Account websites

### Redeem API

Redeem Contacts or Accounts by providing IDs. Response includes data as per subscribed Entitlement.

- Provide one or up to 20 IDs inside list of redeemIDs
- Usual workflow is using Search API and finding IDs of your interest before redeeming

#### Redeem Contacts by IDs

```
POST https://app.cognism.com/api/search/contact/redeem?mergePhonesAndLocations=
```

Example request body:
```json
{
  "redeemIds": [
    "YmVkOWMxZDItYjgwNi0zN2I2LTk2MzItNzVlZjk5MWE0ODdhO"
  ]
}
```

#### Redeem Accounts by IDs

```
POST https://app.cognism.com/api/search/account/redeem?mergePhonesAndLocations=
```

Example request body:
```json
{
  "redeemIds": [
    "e53fa4ed-cc99-385c-b431-38fd6a6a88e0",
    "efc6d975-8599-38ab-8cfe-e1c42ac16dc6"
  ]
}
```

### Enrich API

Use Enrich API to enrich and update your data. Enrich supports finding single best match according to provided parameters.

MatchScore value depends on how well and how many input parameters have matched returned contact/account profile.

#### Enrich Contact

```
POST https://app.cognism.com/api/search/contact/enrich
```

In order to match contact accurately, use one of search criteria:
- Unique identifiers such as email/sha256 or linkedinUrl
- Combine firstName, lastName and jobTitle with accountName or with account unique identifiers accountWebsite
- Provide as many input parameters as you can for best possible match

Default minMatchScore is 30 and data will not be returned if profile is scored less.

#### Enrich Account

```
POST https://app.cognism.com/api/search/account/enrich
```

In order to match account accurately, use one of search criteria:
- Unique identifiers such as website, domain or linkedinUrl
- Provide as many input parameters as you can for best possible match

Default minMatchScore is 40 and data will not be returned if profile is scored less.

### Entitlement API

#### Get Contact Entitlement Details

```
GET https://app.cognism.com/api/search/entitlement/contactEntitlementSubscription
```

#### Get Account Entitlement Details

```
GET https://app.cognism.com/api/search/entitlement/accountEntitlementSubscription
```

### Filter API

These endpoints provide lookup values for various filters that can be used in search requests.

- Technologies: `GET https://app.cognism.com/api/search/filter/technologiesSearch?lastReturnedKey=&search=&indexSize=20`
- Management Levels: `GET https://app.cognism.com/api/search/filter/managementLevels`
- Company Sizes: `GET https://app.cognism.com/api/search/filter/companySizes`
- Industries: `GET https://app.cognism.com/api/search/filter/industries`
- Job Functions: `GET https://app.cognism.com/api/search/filter/jobFunctions`
- World Regions: `GET https://app.cognism.com/api/search/filter/regions`
- Countries: `GET https://app.cognism.com/api/search/filter/countries`
- States: `GET https://app.cognism.com/api/search/filter/states`
- SIC Codes: `GET https://app.cognism.com/api/search/filter/sic`
- ISIC Codes: `GET https://app.cognism.com/api/search/filter/isic`
- NAICS Codes: `GET https://app.cognism.com/api/search/filter/naics`
- Skills: `GET https://app.cognism.com/api/search/filter/skills`
- Company Types: `GET https://app.cognism.com/api/search/filter/companyTypes`
- Seniority: `GET https://app.cognism.com/api/search/filter/seniority`

### Compliance API

Compliance API exposes three endpoints for checking contact Opt-out status.

- HTTP status code 200 indicates Contact has chosen to Opt-out
- HTTP status code 404 indicates Contact has not chosen to Opt-out

#### Opt-Out List

```
GET https://app.cognism.com/api/search/contact/optOut?pageSize=100
```

#### Search Opt-Out by Email

```
GET https://app.cognism.com/api/search/contact/optOut/email/:email
```

#### Search Opt-Out by Id

```
GET https://app.cognism.com/api/search/contact/optOut/id/:id
```

## Error Handling

With an unexpected response, usually, one of the below HTTP codes will be returned:

| Error Code | Message | Troubleshooting |
|------------|---------|-----------------|
| 400 | Requested page number must be within allowed values | Check page query parameter. Make sure it is a number and within allowed range |
| 400 | Requested page size must be within allowed values | Check pageSize query parameter. Make sure it is a number and within allowed range |
| 400 | Request body invalid JSON | Make sure request contains valid JSON |
| 400 | Search request not supported by subscribed entitlement | Check your Entitlement definition for allowed search fields. Contact Cognism for Entitlement expansion |
