# Domain and business rules

[Español](../es/dominio.md) · [← Back](../../README.en.md)

This document describes **what** Saver does. It is the functional reference for both backend and frontend.
Sections marked ✅ are implemented; those marked 🛠️ are not yet. Partial ones say so.

## Currencies and exchange rate 🛠️

- Supported currencies: **ARS** and **USD**. Each user picks a main currency.
- Exchange rate: **official dollar, sell rate**, fetched from a public API (dolarapi.com). If it fails, it is entered manually.
- Every income or payment in USD stores the rate used. Every month closing stores the rate on the closing day.
- Projections are shown in **nominal** and **real** terms (discounting the currency's inflation, see [Inflation](#inflation-)).

## Income ✅

| Type | Behavior | Included in projections? |
|---|---|---|
| **Fixed** (salary) | Amount with validity dates; changes are versioned from a date | Yes, indefinitely |
| **One-off extra** | An amount in one month (unexpected income, collection, loan) | No |
| **Recurring extra** | Same amount for N months or with no end date | Yes, only in those months |

If a job is paid in parts with different amounts each month, each part is entered manually as a one-off extra.

**Increases** (repeating income): like fixed expenses, every N months a fixed % or **with the currency's inflation**. Only used to estimate future months; the real increase is entered with "Record increase".

## Categories ✅

- System categories: Rent, Utilities, Installments, Other. Users can create their own (e.g. "Food").
- Deleting a category is a **soft delete**: it is no longer available for new expenses, but history keeps its name and color.

## Expenses ✅

An expense is defined once as a **template** that generates monthly **occurrences**.

| Type | Example | Occurrences |
|---|---|---|
| **One-off** | Car repair | One, in the given month |
| **Installments** | 15,000 × 3 starting November | Nov, Dec, Jan — each one labeled "installment n/N" |
| **Indefinite fixed** | Rent | Every month until cancelled |

- **Due date**: last day of the month by default; another date can be set when creating the expense and edited on each occurrence.
- **Increases** (fixed expenses): frequency (e.g. every 4 months) and a fixed % **or inflation**: the increase in month A is the accumulated inflation of the previous N months (A−N … A−1), official where available and projected for the rest. When the real increase arrives, the new amount is entered with its effective date and kept in history.
- **Variable amount** (electricity, gas): the template has an estimated amount and each month's occurrence amount can be adjusted. Projections will use the **average of the last 3 real payments** once payments exist (stage 3); today they use the estimate.
- **Occurrences**: generated automatically from templates up to the current month (when a month is requested). Future months are computed without being stored. Unpaid occurrences from previous months show up as **overdue** in the current month.
- **Editing**: type, currency, start month and number of installments are not editable (delete and recreate). The amount changes through “Record increase” to keep history; occurrences without payments are recalculated.

## Payments and receipts 🛠️

- Occurrence states: **PENDING → PARTIAL → PAID**, or **OVERDUE** if the due date passed before completion.
- An occurrence accepts **multiple payments** (e.g. credit card minimum payment). Each payment has amount, date, currency, exchange rate, notes and **multiple attachments** (PDF, PNG, JPEG…).
- The UI lists expenses sorted by due date, showing days left, installment number and a **Pay** button that opens the payment form.

## Month closing 🛠️

- Runs **automatically at the end of every month**, in the user's time zone.
- Each closing has an **internal UUID** and a per-user **readable identifier** (`CIE-2026-10`).
- Stores an **immutable snapshot**: income, expenses by category (paid and unpaid), savings, balance and the day's exchange rate.
- Unpaid expenses are recorded in the closing and **carried over to the next month as OVERDUE**, first in the list.

## Savings 🛠️

- Users can declare how much they saved in a month.
- If nothing is declared, **the whole monthly surplus** (income − expenses) is considered savings.

## Inflation ✅

- **Official sources**, downloaded once a day (and on startup) and stored per month: **ARS** = INDEC CPI via [ArgentinaDatos](https://argentinadatos.com) (no key); **USD** = US CPI-U (series `CUUR0000SA0`) from the public [BLS](https://www.bls.gov/developers/) API (no key up to 25 queries/day). If a source does not respond (no internet), a warning is logged and the stored data keeps being used.
- **Inflation of each month**: the official figure when available; otherwise (recent and future months) the **projected** rate:
  1. the user's **manual** rate for that currency, if set;
  2. otherwise the **estimate** = geometric mean of the last 3 official months (`INFLATION_ESTIMATE_MONTHS`);
  3. no data and no manual rate: 0%.
- **Accumulated inflation** between two months = Π (1 + monthly inflation) − 1.
- Used for: fixed expense and income increases **tied to inflation**, projections **in today's money** and history **in real terms**.

## History and comparisons ✅

- Built from the real data of each past month (and the current one): active income and generated expenses. An expense counts as the paid amount when paid; otherwise the expected amount (with that month's adjustments).
- **Month savings** = income − all expenses. Once month closing and declared savings exist (payments stage), that value will be used.
- Monthly series for a period (default: the last 12 months): income, expenses, savings and expenses by category.
- **Category filter**: affects only the expenses shown; savings always consider all expenses.
- Summary: last month's savings vs. the previous one (amount and %), average savings, total savings and change in expenses from the first to the last month.
- **Compare two months**: for each expense, the amount in each month, difference and %; status *up / down / same / new / ended*. Also per category, income, expenses and savings, plus average savings since the chosen month.
- **Inflation**: each month shows its inflation (official or estimated) and a factor to bring it to the last month's money; the screen can show everything **adjusted for inflation**. The summary and the comparison report the period's accumulated inflation, the change in expenses after inflation, savings in real terms and, per expense, how much it rose **above or below inflation**.
- At most 120 months per request.

## Projections ✅

From the current month (included) for N months (1 to 120):

- **Income**: fixed and active recurring extras; one-month extras only count in their month. If they have an increase rule (% or inflation) it applies to future months; otherwise they stay constant.
- **Expenses**: the current month uses what was already generated (with its adjustments). Future months use templates: remaining installments, fixed expenses with **estimated increases** (every N months, % or inflation) and **variable amounts with the average of the last 3 months**.
- **Accumulated balance** = money available today (optional) + each month's savings.
- **In today's money**: each month's savings are divided by the accumulated inflation since the current month, using the [currency's inflation](#inflation-). The screen can try another inflation rate for that simulation only.
- **Monthly events**: last installment of each purchase, estimated expense and income increases (whenever the amount goes up, even if the current month's increase was not recorded) and end of recurring income.
- Summary: final balance (nominal and real), month installments end, average monthly savings and average after installments, months with negative balance.
- A single currency is projected (the user's main one); activity in another currency triggers a warning.

## Home timeline ✅

- Chart of income and expenses per month **from a chosen month for N months** (1 to 120); by default, 6 months before the current one, the current one and 6 after.
- Earlier months come from history (actual); the current and following ones from the projection. Today is marked.

## Password recovery ✅

- From the login screen, “Forgot your password?” asks for the email and always answers the same, whether the account exists or not.
- If it exists, an email (in the user's language) is sent with a single-use link valid for **30 minutes**. Requesting a new one invalidates the previous; at most 3 requests every 15 minutes.
- Setting the new password signs the user out of every session. It also lets a Google-created account add a password.

## Investment simulator 🛠️

- Annual rate as **nominal (TNA) or effective (TEA)**, compounded monthly.
- Contributions: each month's declared savings or, if none, the surplus.
- Nominal and real results.
- Multiple saved scenarios per user; the period (start-end) is remembered on the next visit.

## Reports 🛠️

Printable view in the frontend, PDF generated in the backend and Excel/CSV export.
