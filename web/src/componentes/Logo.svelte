<script lang="ts">
  let { tam = 22 }: { tam?: number } = $props();
</script>

<svg viewBox="0 0 24 24" width={tam} height={tam} fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
  <path d="M8.8 7h6.4" />
  <path d="M9.6 7v2.6M14.4 7v2.6" />
  <path d="M9.6 9.6C6.4 10.2 4 11.8 4 14.4V19a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4.6c0-2.6-2.4-4.2-5.6-4.8" />
  <path d="M4 15.4h16V19a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" fill="currentColor" stroke="none" />
  <path d="M11.6 13.2l3.4-7" />
  <path d="M20.6 1.2c-3.4.6-5.8 2.7-6.8 5.6l1.6 1.1c2.6-1.6 4.4-3.8 5.2-6.7z" fill="currentColor" stroke="none" />
</svg>
