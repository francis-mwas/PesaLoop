# PesaLoop Backend

Chamaa & SACCO management platform. Java 21 · Spring Boot 3.4 · PostgreSQL 16 · Maven.

---

## Quick start (local, no Docker)

### 1. Install PostgreSQL locally

**macOS**
```bash
brew install postgresql@16
brew services start postgresql@16
```

**Ubuntu / Debian**
```bash
sudo apt update && sudo apt install postgresql postgresql-contrib
sudo service postgresql start
```

**Windows**
Download the installer from https://www.postgresql.org/download/windows/ — tick "pgAdmin" during install.

### 2. Create the database

```bash
# macOS / Linux
psql -U postgres -c "CREATE USER pesaloop WITH PASSWORD 'pesaloop';"
psql -U postgres -c "CREATE DATABASE pesaloop OWNER pesaloop;"

# Windows (pgAdmin → Query Tool)
CREATE USER pesaloop WITH PASSWORD 'pesaloop';
CREATE DATABASE pesaloop OWNER pesaloop;
```

### 3. Configure credentials

Edit `src/main/resources/application-dev.yml` — only two things need changing for basic startup:

```yaml
pesaloop:
  mpesa:
    consumer-key: your-sandbox-consumer-key     # from developer.safaricom.co.ke
    consumer-secret: your-sandbox-consumer-secret
```

For pure local testing without M-Pesa calls at all, leave them blank — only STK Push and C2B URL registration will fail; everything else works fine.

### 4. Run

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway runs all 8 migrations automatically on first start. Seed data (test users, groups, loans) is loaded by `V8__seed_test_data.sql`.

Open Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## How M-Pesa paybill integration works

This is what you asked about — here is the complete picture.

### What PesaLoop does and does not do

PesaLoop is a **record-keeping platform**. It never holds money.

| Action | Who initiates | How PesaLoop is involved |
|--------|--------------|--------------------------|
| Member pays contribution | Member, from their M-Pesa | Receives webhook, auto-records |
| Loan disbursement | Treasurer, from group's M-Pesa | Generates instruction, treasurer sends, enters M-Pesa ref to confirm |
| STK Push | System (optional) | Prompts member's phone, receives webhook on success |

### The three payment entry points

#### Entry Point 1 — C2B Paybill (member-initiated, fully automatic)

This is the main payment flow. The member pays directly from their phone:

```
M-Pesa menu:
  Lipa na M-Pesa
  → Pay Bill
  → Business No:   522533          ← group's paybill number
  → Account No:    M-017           ← member types their member number
  → Amount:        51000
  → PIN
```

What happens behind the scenes:
1. Safaricom receives the payment
2. Safaricom calls our webhook: `POST /webhooks/mpesa/c2b/confirmation`
3. `C2bPaymentProcessor` receives the call
4. It reads `BillRefNumber` = "M-017" and `BusinessShortCode` = "522533"
5. Looks up the group by shortcode → finds Wanjiku Table Banking
6. Resolves the member by "M-017" → finds Alice Watiri
7. Finds her open contribution entry for the current cycle
8. Updates: `paid_amount += 51000`, sets status to PAID
9. Credits her `savings_balance`
10. Records in `payment_records` with the M-Pesa transaction ID

**The account reference (what the member types)** is resolved with 5 fallback strategies:
1. Exact member number: `M-017`, `M-017`, `m-017` (case-insensitive)
2. Number only: `017` → resolved as `M-017`
3. No dash: `M017` → resolved as `M-017`
4. Phone number: if they forgot their member number, their phone resolves them
5. Name partial match: `Alice` → last resort, flags for manual review

**Payments that cannot be matched** are saved to `unmatched_payments` table. The admin resolves them via `POST /api/v1/payments/unmatched/{id}/resolve`.

#### Entry Point 2 — STK Push (system-initiated)

Admin or treasurer triggers this from the PesaLoop dashboard:

```
POST /api/v1/contributions/stk-push
{
  "memberId": "...",
  "cycleId": "...",
  "amount": 51000
}
```

Member's phone rings with a PIN prompt. They enter their PIN. Safaricom calls our webhook: `POST /webhooks/mpesa/stk/callback`. `MpesaWebhookService` processes it and credits the contribution.

#### Entry Point 3 — Manual entry (admin/treasurer)

```
POST /api/v1/contributions/manual
{
  "memberId": "...",
  "cycleId": "...",
  "amount": 51000,
  "paymentMethod": "MANUAL_CASH",
  "reference": "RCPT-001"
}
```

Used for cash collected at meetings, bank transfers, cheque deposits.

---

### Setting up M-Pesa integration (Daraja sandbox)

#### Step 1: Get sandbox credentials

1. Go to **https://developer.safaricom.co.ke/**
2. Create an account → **My Apps** → **Create App**
3. Enable these APIs:
   - **Lipa na M-Pesa Sandbox** (for STK Push)
   - **Customer To Business (C2B) Sandbox** (for paybill payments)
4. Copy **Consumer Key** and **Consumer Secret** → paste into `application-dev.yml`

#### Step 2: Expose localhost to the internet (for callbacks)

Safaricom needs to call your `/webhooks/mpesa/c2b/confirmation` endpoint. Your localhost isn't reachable from the internet. Use ngrok:

```bash
# Install ngrok
brew install ngrok              # macOS
# or download from https://ngrok.com/download

# Expose your local server
ngrok http 8080

# You'll see something like:
# Forwarding   https://abc123.ngrok.io → http://localhost:8080
```

Copy the `https://abc123.ngrok.io` URL and set it in `application-dev.yml`:
```yaml
pesaloop:
  mpesa:
    callback-base-url: https://abc123.ngrok.io
```

Restart the app after changing this.

#### Step 3: Register your C2B URLs with Safaricom

Once the app is running with your ngrok URL, call this endpoint once:

```bash
curl -X POST http://localhost:8080/api/v1/payment-accounts \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountType": "MPESA_PAYBILL",
    "provider": "SAFARICOM_MPESA",
    "accountNumber": "174379",
    "accountName": "Test Chamaa",
    "isCollection": true,
    "isDisbursement": false,
    "isPrimary": true
  }'
```

`174379` is the **Safaricom sandbox test shortcode** (paybill). The API will automatically call `registerC2bUrls()` which tells Safaricom: "when someone pays to shortcode 174379, send the confirmation to `https://abc123.ngrok.io/webhooks/mpesa/c2b/confirmation`".

#### Step 4: Simulate a C2B payment

Use the Safaricom sandbox simulator: **https://developer.safaricom.co.ke/test_stk**

Or simulate it directly against your running server (same payload Safaricom sends):

```bash
curl -X POST http://localhost:8080/webhooks/mpesa/c2b/confirmation \
  -H "Content-Type: application/json" \
  -d '{
    "TransactionType": "Pay Bill",
    "TransID": "LGR219G3EY",
    "TransAmount": "51000.00",
    "BusinessShortCode": "174379",
    "BillRefNumber": "M-001",
    "MSISDN": "254712000001",
    "FirstName": "Alice",
    "LastName": "Watiri"
  }'
```

Expected response: `{"ResultCode":"0","ResultDesc":"Accepted"}`

Then check Alice's contribution was recorded:
```bash
curl http://localhost:8080/api/v1/contributions/cycles/{cycleId}/summary \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### Step 5: STK Push test

```bash
curl -X POST http://localhost:8080/api/v1/contributions/stk-push \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "memberId": "33333333-0000-0000-0001-000000000005",
    "cycleId": "55555555-0000-0000-0001-000000000004",
    "amount": 12000,
    "phoneNumber": "254711000005"
  }'
```

Safaricom sandbox STK Push doesn't ring a real phone — it just returns a success. To simulate the callback:

```bash
curl -X POST http://localhost:8080/webhooks/mpesa/stk/callback \
  -H "Content-Type: application/json" \
  -d '{
    "Body": {
      "stkCallback": {
        "MerchantRequestID": "29115-34620561-1",
        "CheckoutRequestID": "ws_CO_191220191020363925",
        "ResultCode": 0,
        "ResultDesc": "The service request is processed successfully.",
        "CallbackMetadata": {
          "Item": [
            { "Name": "Amount",             "Value": 12000 },
            { "Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV" },
            { "Name": "TransactionDate",    "Value": 20250115103033 },
            { "Name": "PhoneNumber",        "Value": 254711000005 }
          ]
        }
      }
    }
  }'
```

---

### Bank paybill

Some banks (Equity, KCB, Co-op) issue their own M-Pesa paybill numbers. These work exactly the same as an M-Pesa paybill — the member pays using M-Pesa, Safaricom forwards to us via the same C2B webhook.

To add a bank paybill:

```bash
POST /api/v1/payment-accounts
{
  "accountType": "BANK_PAYBILL",
  "provider": "EQUITY_BANK",
  "accountNumber": "247247",
  "accountName": "Wanjiku Table Banking — Equity",
  "isCollection": true,
  "isDisbursement": false,
  "isPrimary": false
}
```

The `accountNumber` here is the bank's M-Pesa paybill (e.g. Equity is `247247`, KCB is `522522`). C2B registration happens automatically just like a regular M-Pesa paybill.

---

## Seed data (loaded automatically by V8 migration)

All passwords: **`test1234`**

### Groups

| Slug | Type | Status | Scenario |
|------|------|--------|----------|
| `wanjiku-table-banking` | TABLE_BANKING | ACTIVE (paid) | 5 members, loans, paybill `522533` |
| `nairobi-welfare-group` | WELFARE | ACTIVE (paid) | 3 members, MGR rotation |
| `mombasa-bodaboda-daily` | MERRY_GO_ROUND | TRIAL | Daily KES 20 contributions, till `891234` |
| `suspended-test-group` | TABLE_BANKING | SUSPENDED | Tests 402 enforcement |
| `fresh-trial-group` | INVESTMENT | TRIAL | Fresh signup, no activity |

### Users

| Phone | Name | Role in Group 1 | Scenario |
|-------|------|-----------------|----------|
| `254712000001` | Alice Wanjiru Watiri | ADMIN | Has active KES 100K loan (`LN-2025-0001`) |
| `254722000002` | Brian Mwangi Kahara | TREASURER | KES 350K loan in PENDING_DISBURSEMENT |
| `254733000003` | Catherine Njeri Kamau | MEMBER | Partial payment (KES 27K of 39K), eligible for loan |
| `254700000004` | Daniel Kiprotich Karoki | MEMBER | Partial payment (KES 18K of 45K) |
| `254711000005` | Esther Akinyi Odhiambo | MEMBER | KES 3K arrears — ineligible for loans |
| `254777000008` | Hassan Abdi Mohamed | ADMIN of Group 4 | Tests 402 suspended group |

### Pre-loaded state

- **Cycle 4** is open for Group 1. Alice and Brian are PAID. Catherine and Daniel are PARTIAL. Esther is PENDING.
- **Alice's loan** (`LN-2025-0001`): KES 100,000 ACTIVE, 1 instalment paid, KES 73,333 outstanding.
- **Brian's loan** (`LN-2025-0002`): KES 350,000 in `PENDING_DISBURSEMENT` — a disbursement instruction is in the queue waiting for the treasurer to confirm.
- **Group 4** is SUSPENDED — all write endpoints return `402 Payment Required`.

---

## API quick reference

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `POST /api/v1/auth/register` | None | Create account |
| `POST /api/v1/auth/otp/verify` | None | Verify phone |
| `POST /api/v1/auth/login` | None | Login → JWT token |
| `GET /api/v1/auth/my-groups` | JWT | List my groups |
| `POST /api/v1/members` | ADMIN | Add member to group |
| `GET /api/v1/members` | ADMIN/TREASURER | List members |
| `POST /api/v1/contributions/manual` | ADMIN/TREASURER | Record cash/bank payment |
| `POST /api/v1/contributions/stk-push` | ADMIN/TREASURER | Initiate M-Pesa PIN prompt |
| `GET /api/v1/contributions/cycles/{id}/summary` | ADMIN+ | Cycle totals |
| `POST /api/v1/payments/paybill/register` | ADMIN | Connect paybill to Safaricom |
| `GET /api/v1/payments/unmatched` | ADMIN | See unresolved C2B payments |
| `POST /api/v1/loans/applications` | MEMBER+ | Apply for loan |
| `PUT /api/v1/loans/applications/{id}/process` | ADMIN | Approve/reject |
| `POST /api/v1/loans/{id}/disburse` | ADMIN/TREASURER | Issue disbursement instruction |
| `GET /api/v1/disbursements/pending` | ADMIN/TREASURER | Treasurer payment queue |
| `POST /api/v1/disbursements/{id}/confirm` | ADMIN/TREASURER | Confirm manual transfer |
| `POST /api/v1/loans/{id}/repayments` | ADMIN/TREASURER | Record loan repayment |
| `GET /api/v1/payment-accounts` | ADMIN | List payment accounts |
| `POST /api/v1/payment-accounts` | ADMIN | Add M-Pesa/bank account |
| `GET /api/v1/payment-accounts/subscription` | ADMIN | Billing status |
| `POST /webhooks/mpesa/c2b/confirmation` | None | Safaricom C2B callback |
| `POST /webhooks/mpesa/stk/callback` | None | STK Push result callback |

Full interactive docs: **http://localhost:8080/swagger-ui.html**
