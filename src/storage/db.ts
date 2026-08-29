import type { AppExport, Garment, WearRecord } from '../domain/types'

const DB_NAME = 'chuanlemei-db'
const DB_VERSION = 1

type StoreName = 'garments' | 'wearRecords'

export interface AppData {
  garments: Garment[]
  wearRecords: WearRecord[]
}

export async function loadAppData(): Promise<AppData> {
  const [garments, wearRecords] = await Promise.all([
    getAll<Garment>('garments'),
    getAll<WearRecord>('wearRecords'),
  ])
  return {
    garments: garments.sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
    wearRecords: wearRecords.sort((left, right) => right.wornAt.localeCompare(left.wornAt)),
  }
}

export async function saveGarment(garment: Garment): Promise<void> {
  await put('garments', garment)
}

export async function saveWearRecord(record: WearRecord): Promise<void> {
  await put('wearRecords', record)
}

export async function deleteWearRecord(id: string): Promise<void> {
  await remove('wearRecords', id)
}

export async function exportAppData(): Promise<AppExport> {
  const data = await loadAppData()
  return {
    version: 1,
    exportedAt: new Date().toISOString(),
    garments: data.garments,
    wearRecords: data.wearRecords,
  }
}

export async function importAppData(payload: AppExport): Promise<void> {
  if (payload.version !== 1 || !Array.isArray(payload.garments) || !Array.isArray(payload.wearRecords)) {
    throw new Error('导入文件格式不正确')
  }

  await Promise.all([
    ...payload.garments.map((garment) => saveGarment(garment)),
    ...payload.wearRecords.map((record) => saveWearRecord(record)),
  ])
}

function getAll<T>(storeName: StoreName): Promise<T[]> {
  return withStore<T[]>(storeName, 'readonly', (store) => {
    const request = store.getAll()
    return requestToPromise<T[]>(request)
  })
}

function put<T extends { id: string }>(storeName: StoreName, value: T): Promise<void> {
  return withStore<void>(storeName, 'readwrite', async (store) => {
    await requestToPromise(store.put(value))
  })
}

function remove(storeName: StoreName, id: string): Promise<void> {
  return withStore<void>(storeName, 'readwrite', async (store) => {
    await requestToPromise(store.delete(id))
  })
}

function withStore<T>(
  storeName: StoreName,
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => Promise<T>,
): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const transaction = db.transaction(storeName, mode)
        const store = transaction.objectStore(storeName)
        let result: T
        transaction.oncomplete = () => resolve(result)
        transaction.onerror = () => reject(transaction.error ?? new Error('数据库事务失败'))
        operation(store)
          .then((value) => {
            result = value
          })
          .catch(reject)
      }),
  )
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains('garments')) {
        db.createObjectStore('garments', { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains('wearRecords')) {
        db.createObjectStore('wearRecords', { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('数据库打开失败'))
  })
}

function requestToPromise<T = unknown>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('数据库请求失败'))
  })
}
