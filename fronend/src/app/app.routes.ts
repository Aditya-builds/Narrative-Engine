import { Routes } from '@angular/router';
import { CharactersPage } from './characters.page';
import { PersonasPage } from './personas.page';
import { ChatPage } from './chat.page';
import { EntityEditorPage } from './entity-editor.page';

export const routes: Routes = [
  { path: '', component: CharactersPage },
  { path: 'characters/new', component: EntityEditorPage, data: { kind: 'character' } },
  { path: 'characters/:characterName/personas/new', component: EntityEditorPage, data: { kind: 'persona' } },
  { path: 'characters/:characterName/personas', component: PersonasPage },
  { path: 'characters/:characterName/personas/:personaName/chat', component: ChatPage },
  { path: '**', redirectTo: '' }
];

