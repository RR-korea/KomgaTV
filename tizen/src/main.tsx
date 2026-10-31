import { render } from 'preact';
import { App } from './App';
import { registerRemoteKeys } from './platform/keys';
import './styles.css';

registerRemoteKeys();
render(<App />, document.getElementById('app')!);
