package com.saver.expense;

/** Estado persistido. VENCIDO no se guarda: se calcula (vencimiento pasado y no pagado). */
public enum OccurrenceStatus { PENDING, PARTIAL, PAID }
