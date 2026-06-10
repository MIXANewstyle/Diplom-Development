import { Client } from '@stomp/stompjs'
import type { StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from '../stores/authStore'

const getWsBaseUrl = () => {
  return (
    import.meta.env.VITE_WS_BASE_URL ??
    import.meta.env.VITE_API_BASE_URL ??
    'http://localhost:8080'
  )
}

export class StompClientWrapper {
  private client: Client | null = null
  private connectionPromise: Promise<void> | null = null
  private onConnectCallbacks: Array<() => void> = []

  /** Register a callback that runs on every connect/reconnect. Returns an unsubscribe function. */
  onReconnect(callback: () => void): () => void {
    this.onConnectCallbacks.push(callback)
    if (this.client?.connected) {
      callback()
    }
    return () => {
      this.onConnectCallbacks = this.onConnectCallbacks.filter((cb) => cb !== callback)
    }
  }

  connect(): Promise<void> {
    if (this.client?.connected) {
      return Promise.resolve()
    }
    if (this.connectionPromise) return this.connectionPromise

    this.connectionPromise = new Promise((resolve, reject) => {
      const token = useAuthStore.getState().token

      if (!token) {
        this.connectionPromise = null
        reject(new Error('No auth token available for STOMP connection'))
        return
      }

      let resolved = false

      this.client = new Client({
        webSocketFactory: () => new SockJS(`${getWsBaseUrl()}/ws`),
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        reconnectDelay: 3000,
        // debug: (msg) => console.log('[STOMP]', msg),
        onConnect: () => {
          if (!resolved) {
            resolved = true
            resolve()
          }
          this.onConnectCallbacks.forEach((cb) => cb())
        },
        onStompError: (frame) => {
          console.error('Broker reported error: ' + frame.headers['message'])
          console.error('Additional details: ' + frame.body)
          if (!resolved) {
            this.connectionPromise = null
            reject(new Error(frame.headers['message']))
          }
        },
        onWebSocketClose: () => {
          this.connectionPromise = null
        },
        onDisconnect: () => {
          this.connectionPromise = null
        },
        onWebSocketError: (event) => {
          console.error('WebSocket error:', event)
        },
      })

      this.client.activate()
    })

    return this.connectionPromise
  }

  disconnect() {
    if (this.client && this.client.active) {
      this.client.deactivate()
    }
    this.client = null
    this.connectionPromise = null
  }

  subscribe(destination: string, callback: (body: any) => void): StompSubscription | null {
    if (!this.client || !this.client.connected) {
      console.warn('Cannot subscribe: STOMP client is not connected')
      return null
    }

    return this.client.subscribe(destination, (message) => {
      try {
        const body = message.body ? JSON.parse(message.body) : null
        callback(body)
      } catch (e) {
        console.error('Failed to parse STOMP message body:', e)
        callback(message.body)
      }
    })
  }

  publish(destination: string, body: any) {
    if (!this.client || !this.client.connected) {
      console.warn('Cannot publish: STOMP client is not connected')
      return
    }

    this.client.publish({
      destination,
      body: JSON.stringify(body),
    })
  }
}

export const stompClient = new StompClientWrapper()
