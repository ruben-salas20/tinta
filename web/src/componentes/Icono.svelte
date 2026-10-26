<script lang="ts">
  let { n, s = false, red = false }: { n: string; s?: boolean; red?: boolean } = $props();
</script>

{#if red}
  <svg class="red" aria-hidden="true"><use href="/redes.svg#{n}" /></svg>
{:else}
  <svg class="i" class:s aria-hidden="true"><use href="/iconos.svg#{n}" /></svg>
{/if}
