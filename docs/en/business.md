# Saver: business documentation

## Purpose

Saver is a web application intended to help people organize their financial life in one place. Its product goal is to make income, expenses, savings and investments easier to understand and use for decisions. The solution is being delivered in stages: it currently provides a secure, personalized access foundation for the future financial tools.

## What a person can do today

1. Create an account with a name, email address and password.
2. Sign in and sign out with email and password.
3. Sign in with Google when an administrator has enabled that option.
4. Safely restore their session when returning to the app.
5. View and update their profile: name, language, primary currency and time zone.
6. Use Spanish or English and choose a light, dark, or system theme.
7. Record **income**: fixed (salary), one-month extras or extras repeating for N months or indefinitely, and record raises from a given month.
8. Record **expenses**: one-off, **installments** (e.g. 15,000 × 3 months, labeled “installment n/N”) and **monthly fixed** (rent, utilities) with their own due day or the last day of the month, variable amounts and estimated periodic increases (e.g. +10% every 4 months).
9. Recover their password with a link sent by **email** (valid 30 minutes, single use).
10. Review **history**: income, expenses and savings per month, filter by category and **compare two months** (which expenses went up or down, by how much and %, savings vs. previous month and average).
11. See **projections** for 6–60 months: how much they will have saved, when installments end, estimated increases and the result in today's money using estimated inflation.
12. Use each currency's **official inflation** (INDEC for pesos, US CPI for dollars), downloaded automatically every day: to project in today's money, for expenses and income that **increase with inflation** and to see history **in real terms** (which expenses rose more than inflation). A manual rate can be set for months without data.
13. See on Home a chart of income and expenses **from any month for N months**, with the actual past, the projected future and today marked.
14. Create, edit and delete their own **categories** (e.g. “Food”); deleted ones are kept in history.
15. See the **month balance** (income, expenses, pending, overdue), **upcoming due dates** sorted by date with days left, an expenses-by-category chart and the past + projection chart. Browse any month; future months are shown as estimates.

Each person can access only their own account. Email identifies the profile and cannot be changed from settings.

## Main journey

A new person selects a language, creates an account or uses Google, then enters their salary and fixed and installment expenses; **Home** shows the month balance, what is due first and how the next months look. In **Settings**, they choose how the product should work for them: currency (Argentine peso or US dollar), language, time zone and appearance. Signing out ends their access in that browser.

## Capabilities being prepared

The menu communicates the product direction, but these areas are not yet active:

| Planned capability | Expected value |
|---|---|
| Payments and receipts | Mark expenses as paid (also partially), attach invoices and close months automatically. The “Pay” button is already visible but disabled. |
| Declared savings | Record how much was set aside each month. |
| Investments | Simulate and save scenarios. |
| Reports | Download or share PDF, Excel, or CSV summaries. |

These must not be presented as available until their screens, API, and business rules are implemented and tested.

## Experience and privacy rules

- Passwords must have at least 8 characters; the server limits them to 72 bytes for security compatibility.
- Readable passwords are never stored; a secure representation is stored instead.
- The app restores sessions through a protected browser mechanism; the short-lived access credential is not persistently stored in the browser.
- The profile language takes precedence after sign-in. Before that, the selected or browser-detected language is used.
- Currency and time zone are profile preferences. The current release has no financial movements to convert and no monthly close process.

## Suggested future product indicators

As financial features arrive, track profile completion, monthly active people, movements per person, monthly-close completion, projection use, and exported reports. These are business recommendations, not metrics collected by the current implementation.
