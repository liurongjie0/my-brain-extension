import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({ userId: localStorage.getItem('userId') || '' }),
  actions: {
    ensureUserId() {
      if (!this.userId) {
        this.userId = 'u-' + Math.floor(Date.now() % 1e9) + '-' + Math.floor(Math.random() * 1e4)
        localStorage.setItem('userId', this.userId)
      }
      return this.userId
    }
  }
})
