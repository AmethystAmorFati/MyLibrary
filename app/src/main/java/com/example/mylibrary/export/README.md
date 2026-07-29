# Export package

`report/` resolves monthly and annual report configuration into an immutable
`ReportDataSnapshot`. Records and works are scoped by `Record.startDate`;
activity summaries use `Activity.date`; quotes use their reliable
`Quote.createdTime`.

This round intentionally contains no PDF, PNG, image renderer, sharing flow, or
placeholder report file. A successful settings action only prepares the data
snapshot for a later renderer.
