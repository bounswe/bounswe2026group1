import { describe, it, expect, vi, beforeEach } from 'vitest'
import { applyReportUpdatedToCache, reportKeys } from './useReports.js'

/**
 * applyReportUpdatedToCache is a pure function that calls queryClient.setQueryData.
 * We test it in isolation with a minimal queryClient stub.
 */

function makeQueryClientStub(initialData) {
  let cache = { [JSON.stringify(reportKeys.lists())]: initialData }

  return {
    setQueryData(key, updater) {
      const k = JSON.stringify(key)
      cache[k] = updater(cache[k])
    },
    getData(key) {
      return cache[JSON.stringify(key)]
    },
  }
}

const BASE_REPORT = {
  id: 1,
  title: 'Broken Elevator',
  status: 'unverified',
  agrees: 3,
  disagrees: 1,
}

describe('applyReportUpdatedToCache', () => {
  it('updates agrees on a matching report', () => {
    const qc = makeQueryClientStub([BASE_REPORT])
    applyReportUpdatedToCache(qc, { reportId: 1, agrees: 7, disagrees: 1, status: 'PENDING' })
    const [updated] = qc.getData(reportKeys.lists())
    expect(updated.agrees).toBe(7)
  })

  it('updates disagrees on a matching report', () => {
    const qc = makeQueryClientStub([BASE_REPORT])
    applyReportUpdatedToCache(qc, { reportId: 1, agrees: 3, disagrees: 5, status: 'PENDING' })
    const [updated] = qc.getData(reportKeys.lists())
    expect(updated.disagrees).toBe(5)
  })

  it('maps VERIFIED status to "verified"', () => {
    const qc = makeQueryClientStub([BASE_REPORT])
    applyReportUpdatedToCache(qc, { reportId: 1, agrees: 10, disagrees: 0, status: 'VERIFIED' })
    const [updated] = qc.getData(reportKeys.lists())
    expect(updated.status).toBe('verified')
  })

  it('maps PENDING status to "unverified"', () => {
    const qc = makeQueryClientStub([BASE_REPORT])
    applyReportUpdatedToCache(qc, { reportId: 1, agrees: 1, disagrees: 1, status: 'PENDING' })
    const [updated] = qc.getData(reportKeys.lists())
    expect(updated.status).toBe('unverified')
  })

  it('does not modify other reports in the list', () => {
    const OTHER = { id: 2, title: 'Other', status: 'verified', agrees: 5, disagrees: 0 }
    const qc = makeQueryClientStub([BASE_REPORT, OTHER])
    applyReportUpdatedToCache(qc, { reportId: 1, agrees: 99, disagrees: 0, status: 'VERIFIED' })
    const list = qc.getData(reportKeys.lists())
    expect(list[1]).toEqual(OTHER)
  })

  it('returns the list unchanged when reportId does not match', () => {
    const qc = makeQueryClientStub([BASE_REPORT])
    applyReportUpdatedToCache(qc, { reportId: 999, agrees: 99, disagrees: 0, status: 'VERIFIED' })
    const [report] = qc.getData(reportKeys.lists())
    expect(report).toEqual(BASE_REPORT)
  })

  it('returns undefined unchanged when cache is empty', () => {
    const qc = makeQueryClientStub(undefined)
    applyReportUpdatedToCache(qc, { reportId: 1, agrees: 1, disagrees: 0, status: 'PENDING' })
    expect(qc.getData(reportKeys.lists())).toBeUndefined()
  })
})
