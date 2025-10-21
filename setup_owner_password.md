# Setting Up Password for upday@sunflaguk.com

## Step 1: Find the Customer ID

Run this SQL query in your database to find Udayan's customer ID:

```sql
SELECT customer_id, name, email, customer_type, is_property_owner
FROM customer
WHERE email = 'upday@sunflaguk.com';
```

## Step 2: Trigger Password Setup Email

Once you have the customer ID (let's call it `CUSTOMER_ID`), you have two options:

### Option A: Via Browser (as logged-in employee)

1. Log in to your system as an employee/manager
2. Navigate to: `https://spoutproperty-hub.onrender.com/employee/customer/CUSTOMER_ID`
3. Look for a button or link to send password setup email
4. OR directly POST to: `https://spoutproperty-hub.onrender.com/employee/customer/CUSTOMER_ID/send-password-setup`

### Option B: Via curl (with authentication)

```bash
curl -X POST \
  'https://spoutproperty-hub.onrender.com/employee/customer/CUSTOMER_ID/send-password-setup' \
  -H 'Cookie: JSESSIONID=your_session_cookie' \
  -L
```

## What Happens

1. The system creates a `customer_login_info` record for upday@sunflaguk.com
2. Generates a secure token that expires in 72 hours
3. Sends an email to upday@sunflaguk.com with a password setup link
4. The link will be: `https://spoutproperty-hub.onrender.com/set-password?token=GENERATED_TOKEN`

## For Immediate Setup (Manual)

If you need to set up the password immediately without waiting for email:

1. Run the first SQL query to get the customer ID
2. Access this endpoint as described above
3. The system will return either:
   - Success message with confirmation email was sent
   - OR a warning message with the token if email fails
4. If you get the token, you can manually construct the link and send it to Udayan

## The Password Setup Link

When Udayan clicks the link, he'll see a form at `/set-password?token=XXXXX` where he can:
1. Enter a new password
2. Confirm the password
3. Submit to activate his account

After setting the password, he can log in at:
`https://spoutproperty-hub.onrender.com/customer-login`

## Troubleshooting

**If email doesn't send:**
- Check that you're logged in with Google OAuth and have Gmail permissions
- The system will show you the token in the success message
- You can manually share the link with Udayan

**If customer not found:**
- Verify the email address in the database
- Check if there are multiple customers with similar emails
- Make sure the customer record exists

**If password already set:**
- The system will warn you that password is already set
- In this case, use the password reset flow instead
