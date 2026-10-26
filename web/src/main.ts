import { mount } from 'svelte';
import './estilos.css';
import './web.css';
import './vista-articulos.css';
import './vista-editor.css';
import './vista-exportar.css';
import './vista-calendario.css';
import './app.css';
import App from './App.svelte';

mount(App, { target: document.getElementById('app')! });
