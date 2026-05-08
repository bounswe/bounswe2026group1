function DataTable({ columns, rows, getRowKey, isLoading, isError, error, emptyLabel = 'No results' }) {
  return (
    <div className="rounded-xl bg-surface-container-lowest border border-outline-variant overflow-hidden">
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-surface-container">
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key}
                  className="text-left px-4 py-3 font-semibold text-on-surface-variant whitespace-nowrap"
                  style={col.width ? { width: col.width } : undefined}
                >
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-8 text-center text-on-surface-variant">
                  Loading…
                </td>
              </tr>
            )}
            {isError && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-8 text-center text-error">
                  {error?.message || 'Failed to load data'}
                </td>
              </tr>
            )}
            {!isLoading && !isError && rows.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-8 text-center text-on-surface-variant">
                  {emptyLabel}
                </td>
              </tr>
            )}
            {!isLoading && !isError &&
              rows.map((row) => (
                <tr key={getRowKey(row)} className="border-t border-outline-variant hover:bg-surface-container-low">
                  {columns.map((col) => (
                    <td key={col.key} className="px-4 py-3 align-middle">
                      {col.render ? col.render(row) : row[col.key]}
                    </td>
                  ))}
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default DataTable
