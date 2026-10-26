<script lang="ts">
  import { api } from '../lib/api';
  import Logo from '../componentes/Logo.svelte';

  let { alEntrar }: { alEntrar: () => void } = $props();
  let clave = $state('');
  let error = $state('');
  let ocupado = $state(false);

  async function enviar(e: SubmitEvent) {
    e.preventDefault();
    ocupado = true;
    error = '';
    try {
      await api.entrar(clave);
      alEntrar();
    } catch {
      error = 'La clave no es correcta.';
      clave = '';
    } finally {
      ocupado = false;
    }
  }
</script>

<main class="entrar">
  <form onsubmit={enviar}>
    <div class="marca"><Logo tam={48} /><h1>Tinta</h1></div>
    <label for="clave">Clave</label>
    <!-- svelte-ignore a11y_autofocus -->
    <input id="clave" type="password" bind:value={clave} autocomplete="current-password" autofocus required />
    {#if error}<p class="error">{error}</p>{/if}
    <button class="btn primario" disabled={ocupado || !clave}>Entrar</button>
  </form>
</main>

<style>
  .entrar { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
  form { width: min(340px, 100%); display: flex; flex-direction: column; gap: 10px; background: var(--surface); border: 1px solid var(--line); border-radius: 16px; padding: 28px; box-shadow: var(--shadow); }
  .marca { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
  h1 { font: 600 30px/1 var(--sans); letter-spacing: -0.03em; margin: 0; }
  label { font-size: 13px; color: var(--muted); }
  .error { margin: 0; font-size: 13px; color: var(--text); }
  button { justify-content: center; height: 38px; margin-top: 6px; }
</style>
