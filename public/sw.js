const CACHE_NAME = 'chuanlemei-shell-v1'

self.addEventListener('install', (event) => {
  event.waitUntil(self.skipWaiting())
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') {
    return
  }

  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      try {
        const response = await fetch(request)
        if (new URL(request.url).origin === self.location.origin && response.ok) {
          cache.put(request, response.clone())
        }
        return response
      } catch {
        return (await cache.match(request)) || (await cache.match('/')) || Response.error()
      }
    }),
  )
})
