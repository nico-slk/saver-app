package com.saver.expense;

import java.time.YearMonth;
import java.util.UUID;

record OccurrenceKey(UUID expenseId, YearMonth month) {}
