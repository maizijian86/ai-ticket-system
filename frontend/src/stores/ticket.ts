import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTicketStore = defineStore('ticket', () => {
  const refreshTrigger = ref(0)

  function triggerRefresh() {
    refreshTrigger.value++
  }

  return {
    refreshTrigger,
    triggerRefresh
  }
})
