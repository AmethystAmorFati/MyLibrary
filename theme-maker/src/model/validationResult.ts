export type ValidationSeverity = "error" | "warning";

export interface ValidationIssue {
  field: string;
  message: string;
  severity: ValidationSeverity;
}

export interface ValidationResult<T = undefined> {
  ok: boolean;
  value?: T;
  issues: ValidationIssue[];
}

export function success<T>(value: T): ValidationResult<T> {
  return { ok: true, value, issues: [] };
}

export function failure<T = undefined>(
  field: string,
  message: string
): ValidationResult<T> {
  return {
    ok: false,
    issues: [{ field, message, severity: "error" }]
  };
}

export function mergeIssues(
  ...results: Array<Pick<ValidationResult<unknown>, "issues">>
): ValidationIssue[] {
  return results.flatMap((result) => result.issues);
}
