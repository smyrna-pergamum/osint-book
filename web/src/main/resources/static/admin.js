'use strict';
const $=s=>document.querySelector(s);let me=null,users=[],requests=[],logs=[],keys=[];
const sections=['users','add-user','requests','keys','logs','ai-models'];
let active=sections.includes(location.hash.slice(1))?location.hash.slice(1):'users';
function h(tag,attrs={},...children){const e=document.createElement(tag);for(const [k,v] of Object.entries(attrs)){if(k.startsWith('on'))e.addEventListener(k.slice(2).toLowerCase(),v);else if(k==='class')e.className=v;else if(['checked','disabled'].includes(k))e[k]=v;else if(v!=null)e.setAttribute(k,v)}children.flat(Infinity).forEach(c=>{if(c!=null)e.append(c instanceof Node?c:document.createTextNode(String(c)))});return e}
function notice(message){$('#notice').textContent=tr(message);setTimeout(()=>$('#notice').textContent='',8000)}
async function perform(fn){try{await fn()}catch(e){notice(e.message||'operation_failed')}}
function btn(label,fn,cls=''){return h('button',{type:'button',class:cls,onclick:()=>perform(fn)},tr(label))}
function field(label,el){return h('label',{},tr(label),el)}
function input(name,type='text',extra={}){return h('input',{name,type,...extra})}
function picker(){const s=h('select',{'aria-label':tr('Language')},Object.entries(languages).map(([id,name])=>h('option',{value:id},name)));s.value=language;s.onchange=()=>{localStorage.setItem('osint-language',s.value);location.reload()};return h('label',{class:'admin-language'},h('span',{},tr('Language')),s)}
async function api(path,method='GET',data){const res=await fetch('/api'+path,{method,headers:{'X-OSINT-Request':'1',...(data===undefined?{}:{'Content-Type':'application/json'})},body:data===undefined?undefined:JSON.stringify(data)});if(!res.ok){const err=await res.json().catch(()=>({error:'operation_failed'}));if(res.status===401&&path!='/login')login();throw Error((typeof aiText==='function'?aiText(err.error):tr(err.error))+(err.detail?' — '+err.detail:''))}return res.json()}
function modal(title,body){const d=$('#dialog');d.replaceChildren(h('div',{class:'dialog-head'},h('h2',{},tr(title)),btn('Cancel',()=>d.close())),h('div',{class:'dialog-body'},body));if(!d.open)d.showModal();return d}
function submit(form,fn){form.onsubmit=async e=>{e.preventDefault();const b=form.querySelector('[type=submit]');b.disabled=true;try{await fn(new FormData(form))}catch(err){notice(err.message)}finally{b.disabled=false}};return form}
function login(){me=null;const f=submit(h('form',{class:'form-grid'},field('Username',input('username','text',{required:true,maxlength:40,autocomplete:'username'})),field('Password',input('password','password',{required:true,autocomplete:'current-password'})),h('button',{type:'submit',class:'primary'},tr('Sign in →'))),async data=>{await api('/login','POST',Object.fromEntries(data));await load()});$('#app').replaceChildren(h('div',{class:'login'},h('section',{class:'login-story'},h('h1',{},'OSINT-Book'),h('p',{},tr('Server administration'))),h('section',{class:'login-form'},picker(),h('h2',{},tr('Admin panel')),h('p',{},tr('Sign in with an administrator account.')),f)))}
async function load(){me=await api('/me');[users,requests,logs,keys]=await Promise.all([api('/admin/users'),api('/admin/password-requests'),api('/admin/logs'),api('/admin/keys')]);render()}
function resetPassword(u){const f=submit(h('form',{class:'form-grid'},h('strong',{},u.username),field('New password',input('password','password',{required:true,minlength:12,maxlength:256,autocomplete:'new-password'})),h('button',{type:'submit',class:'primary'},tr('Save'))),async data=>{await api('/admin/users/'+encodeURIComponent(u.username),'PATCH',{password:data.get('password')});$('#dialog').close();if(u.username===me.username)login();else await load();notice('Saved.')});modal('Reset password',f)}
function keyEdit(k){const f=submit(h('form',{class:'form-grid'},field('API key',input('key','password',{required:true,maxlength:4096,autocomplete:'off'})),h('button',{type:'submit',class:'primary'},tr('Save'))),async d=>{await api('/admin/keys/'+encodeURIComponent(k.id),'PUT',{key:d.get('key')});$('#dialog').close();await load()});modal(k.name,f)}
function navigate(section){active=section;history.replaceState(null,'','#'+section);render()}
function deleteUser(u){modal('Delete user?',h('div',{},h('p',{},tr('Permanently delete this account?')),h('p',{},h('strong',{},u.username)),h('p',{class:'muted'},tr('Active sessions and pending password requests will be removed. Shared investigation data and activity logs remain.')),h('div',{class:'dialog-foot'},btn('Cancel',()=>$('#dialog').close()),btn('Delete user',async()=>{await api('/admin/users/'+encodeURIComponent(u.username),'DELETE');$('#dialog').close();await load();notice('User deleted.');},'danger'))))}
function userRow(u){return h('div',{class:'admin-user-row'},h('div',{class:'admin-user-info'},h('div',{class:'admin-avatar','aria-hidden':'true'},u.username.charAt(0).toUpperCase()),h('div',{},h('strong',{},u.username),h('div',{class:'admin-badges'},h('span',{class:'pill'},tr(u.admin?'Administrator':'User')),h('span',{class:'pill '+(u.enabled?'ok':'')},tr(u.enabled?'Enabled':'Disabled'))))),h('div',{class:'actions'},btn('Reset password',()=>resetPassword(u)),u.username!==me.username?btn(u.enabled?'Disable':'Enable',async()=>{await api('/admin/users/'+encodeURIComponent(u.username),'PATCH',{enabled:!u.enabled});await load()}):null,u.username!==me.username?btn('Delete user',()=>deleteUser(u),'danger'):null,u.username!==me.username?btn(u.admin?'Remove administrator':'Make administrator',async()=>{await api('/admin/users/'+encodeURIComponent(u.username),'PATCH',{admin:!u.admin});await load()}):null))}
function usersView(){return h('div',{},h('div',{class:'admin-page-head'},h('div',{},h('h1',{},tr('User accounts')),h('p',{},tr('Manage access and account permissions.'))),btn('+ Add user',()=>navigate('add-user'),'primary')),h('section',{class:'panel admin-panel'},h('div',{class:'admin-section-head'},h('h2',{},tr('All users')),h('span',{class:'pill'},`${users.length} ${tr('accounts')}`)),users.length?users.map(userRow):h('p',{class:'muted'},tr('No users found.'))))}
function addUserView(){const add=submit(h('form',{class:'form-grid admin-form'},field('Username',input('username','text',{required:true,minlength:3,maxlength:40,pattern:'[a-zA-Z0-9_.-]{3,40}',autocomplete:'off'})),field('New password',input('password','password',{required:true,minlength:12,maxlength:256,autocomplete:'new-password'})),h('label',{class:'admin-check'},input('admin','checkbox'),h('span',{},h('strong',{},tr('Administrator')),h('small',{},tr('Allow access to the admin panel.')))),h('div',{class:'actions'},h('button',{type:'submit',class:'primary'},tr('Add user')),btn('Cancel',()=>navigate('users')))),async d=>{await api('/admin/users','POST',{username:d.get('username'),password:d.get('password'),admin:d.has('admin')});active='users';history.replaceState(null,'','#users');await load();notice('Saved.')});return h('div',{},h('div',{class:'admin-page-head'},h('div',{},h('h1',{},tr('Add user')),h('p',{},tr('Create an account for this server.')))),h('section',{class:'panel admin-panel admin-form-panel'},h('h2',{},tr('Account details')),h('p',{class:'muted'},tr('Set a username, password and permission level.')),add))}
function requestsView(){const pending=requests.filter(r=>r.status==='pending');return h('div',{},h('div',{class:'admin-page-head'},h('div',{},h('h1',{},tr('Password reset requests')),h('p',{},tr('Review requests before resetting passwords.')))),h('section',{class:'panel admin-panel'},pending.length?pending.map(r=>h('div',{class:'admin-user-row'},h('div',{},h('strong',{},r.username),h('div',{},h('small',{},r.createdAt))),btn('Reset password',()=>resetPassword({username:r.username})))):h('p',{class:'muted'},tr('No pending requests.'))))}
function keysView(){return h('div',{},h('div',{class:'admin-page-head'},h('div',{},h('h1',{},tr('API keys')),h('p',{},tr('Manage source API keys on the server.')))),h('section',{class:'panel admin-panel'},keys.map(k=>h('div',{class:'admin-user-row'},h('div',{},h('strong',{},k.name),h('div',{},h('span',{class:'pill '+(k.configured?'ok':'')},tr(k.configured?'Key configured':'Missing key')))),h('div',{class:'actions'},btn(k.configured?'Replace key':'Add key',()=>keyEdit(k)),k.stored?btn('Delete stored key',async()=>{await api('/admin/keys/'+encodeURIComponent(k.id),'DELETE');await load()}):null))))) }
function logsView(){const rows=logs.map(l=>h('tr',{},h('td',{},l.at),h('td',{},l.actor),h('td',{},l.action),h('td',{},l.subject)));return h('div',{},h('div',{class:'admin-page-head'},h('div',{},h('h1',{},tr('Activity log')),h('p',{},tr('Latest 500 account and access events.'))),btn('Refresh',async()=>{logs=await api('/admin/logs');render();notice('Activity log refreshed.')})),h('section',{class:'panel admin-panel'},logs.length?h('div',{class:'table-wrap'},h('table',{},h('thead',{},h('tr',{},['Time','Actor','Action','Subject'].map(x=>h('th',{},tr(x))))),h('tbody',{},rows))):h('p',{class:'muted'},tr('No activity yet.'))))}
// ---- AI Models ----
const pill=(text,cls='')=>h('span',{class:'pill '+cls},text);
const aiText=code=>{const c=String(code||'');const m=c.match(/^ai_(download|cloud)_http_(\d+)$/);return m?trf(m[1]==='cloud'?'The provider returned HTTP {code}.':'Hugging Face returned HTTP {code}.',{code:m[2]}):tr(c)};
const gib=b=>b==null?'—':(b/1073741824).toFixed(b<1073741824?2:1)+' GiB';
const num=n=>n==null?'—':Intl.NumberFormat(language,{notation:'compact'}).format(n);
const fitLabel={gpu:['Fits in GPU memory','ok'],cpu:['Runs on CPU (RAM)','ok'],partial:['Splits between GPU and RAM','warn'],too_large:['Too large for this server','error']};
function fitPill(f){if(!f)return null;const [label,cls]=fitLabel[f]||[f,''];return h('span',{class:'pill '+cls},tr(label))}
let aiTab=(()=>{try{return localStorage.getItem('osint-ai-tab')}catch{return null}})()||'recommended',aiHardware=null,aiStatus=null,aiRecommendations=null,aiPopular=null,aiInstalledSignature='',aiLastDownload='';
function aiNav(id){aiTab=id;try{localStorage.setItem('osint-ai-tab',id)}catch{}render()}
/** Installed state changed (download finished or model deleted): cached lists must be fetched again. */
function aiInvalidate(){aiRecommendations=null;aiInstalledSignature='';}
function hardwareView(){
 const w=h('section',{class:'panel admin-panel',id:'ai-hardware'},h('p',{class:'muted'},tr('Detecting hardware…')));
 const fill=hw=>{const gpu=hw.gpus.length?hw.gpus.map(g=>`${g.name}${g.integrated?' ('+tr('integrated, shares RAM')+')':''} · ${gib(g.totalBytes)} · Vulkan`).join(', '):hw.gpuRuntime?tr('No supported GPU detected'):tr('GPU runtime not installed');
  w.replaceChildren(h('h2',{},tr('This server')),h('div',{class:'ai-hw'},
   h('div',{},h('span',{},'CPU'),h('strong',{},trf('{count} threads',{count:hw.cpuCores})),h('small',{},hw.cpuName||hw.cpuArchitecture)),
   h('div',{},h('span',{},'RAM'),h('strong',{},gib(hw.totalRamBytes)),h('small',{},trf('{free} available · up to {budget} for models',{free:gib(hw.availableRamBytes),budget:gib(hw.cpuBudgetBytes)}))),
   h('div',{},h('span',{},'GPU'),h('strong',{},hw.gpus.length?trf('{size} usable',{size:gib(hw.gpuBudgetBytes)}):tr('None')),h('small',{},gpu)),
   h('div',{},h('span',{},tr('Engine')),h('strong',{},'llama.cpp'),h('small',{},[hw.cpuRuntime?'CPU':null,hw.gpuRuntime?'Vulkan GPU':null].filter(Boolean).join(' + ')||tr('Runtime missing')))))};
 if(aiHardware)fill(aiHardware);else queueMicrotask(()=>perform(async()=>{aiHardware=await api('/admin/ai/hardware');if(w.isConnected)fill(aiHardware)}));
 return w}
function downloadBox(d){
 if(!d||d.state==='idle')return null;
 if(d.state==='downloading'||d.state==='preparing'||d.state==='verifying'){
  const pct=d.total?Math.floor(d.downloaded*100/d.total):0,eta=d.bytesPerSecond?Math.round((d.total-d.downloaded)/d.bytesPerSecond):null;
  return h('div',{class:'ai-progress'},h('div',{class:'ai-progress-head'},h('strong',{},d.state==='verifying'?tr('Verifying SHA-256…'):d.state==='preparing'?tr('Preparing download…'):trf('Downloading {file}',{file:d.filename})),d.state!=='verifying'?btn('Cancel download',async()=>{await api('/admin/ai/download/cancel','POST',{});await refreshAi()}):null),
   h('progress',{max:100,value:d.state==='verifying'?100:pct}),
   d.total?h('small',{},`${pct}% · ${trf('{done} of {total}',{done:gib(d.downloaded),total:gib(d.total)})}${d.bytesPerSecond?` · ${(d.bytesPerSecond/1048576).toFixed(1)} MiB/s`:''}${eta!=null?' · '+trf('{time} left',{time:eta>90?Math.round(eta/60)+' min':eta+' s'}):''}`):null)}
 if(d.state==='failed')return h('div',{class:'ai-progress error'},h('strong',{},trf('Download failed: {file}',{file:d.filename||''})),h('p',{},aiText(d.error)));
 if(d.state==='cancelled')return h('div',{class:'ai-progress'},h('strong',{},tr('Download cancelled.')),h('small',{},tr('The partial file was removed.')));
 if(d.state==='complete')return h('div',{class:'ai-progress ok'},h('strong',{},trf(d.alreadyInstalled?'{file} is already installed.':'{file} downloaded and verified.',{file:d.filename})),' ',btn('Go to Installed',()=>aiNav('installed')));
 return null}
function engineBox(s){
 const e=s.engine,m=s.installed.find(x=>x.id===(e.modelId||s.settings?.modelId));
 const name=m?`${m.repository} · ${m.filename}`:'';
 if(e.state==='ready')return h('div',{class:'ai-engine ok'},h('div',{},h('span',{class:'pill ok'},tr('Active')),' ',h('strong',{},name),h('small',{},`${e.backend==='gpu'?'GPU (Vulkan)':'CPU'} · ${trf('context {count} tokens',{count:s.settings?.context})} · ${trf('{count} threads',{count:s.settings?.threads})}`)),btn('Unload',async()=>{await api('/admin/ai/unload','POST',{});await refreshAi()}));
 if(e.state==='loading')return h('div',{class:'ai-engine'},h('div',{},h('span',{class:'pill warn'},tr('Loading')),' ',h('strong',{},name),h('small',{},tr('Starting llama.cpp and loading weights. Large models can take several minutes.'))),btn('Cancel',async()=>{await api('/admin/ai/unload','POST',{});await refreshAi()}));
 if(e.state==='failed')return h('div',{class:'ai-engine error'},h('div',{},h('span',{class:'pill error'},tr('Failed')),' ',h('strong',{},name),h('small',{},aiText(e.error)),e.detail?h('small',{class:'ai-log'},e.detail):null),btn('Dismiss',async()=>{await api('/admin/ai/unload','POST',{});await refreshAi()}));
 return h('div',{class:'ai-engine'},h('div',{},h('span',{class:'pill'},tr('No model active')),h('small',{},tr('Users can pick any installed model or enabled cloud provider when they analyze; a local model is loaded automatically.'))))}
async function refreshAi(){
 const status=await api('/admin/ai/status');aiStatus=status;
 const dlKey=status.download.state+(status.download.id||'');if(dlKey!==aiLastDownload){if(status.download.state==='complete')aiInvalidate();aiLastDownload=dlKey;}
 const dl=$('#ai-download'),en=$('#ai-engine');if(!dl)return;
 dl.replaceChildren(downloadBox(status.download)||'');en.replaceChildren(engineBox(status));
 const st=$('#ai-storage');if(st)st.textContent=trf('Model storage: {size}',{size:gib(status.storageBytes)});
 const sig=JSON.stringify([status.installed.map(m=>m.id),status.engine.state,status.engine.modelId,aiHardware?.gpus?.length]);
 const holder=$('#ai-installed');if(holder&&sig!==aiInstalledSignature){aiInstalledSignature=sig;holder.replaceChildren(...installedRows(status))}
}
function installedRows(s){
 if(!s.installed.length)return [h('p',{class:'muted'},tr('No models installed yet. Choose one under Recommended.'))];
 const gpus=aiHardware?.gpus||[],busy=['loading','ready'].includes(s.engine.state);
 return s.installed.map(m=>{
  const isActive=(s.settings?.modelId===m.id)&&busy;
  const backend=h('select',{name:'backend','aria-label':tr('Device')},h('option',{value:'cpu'},'CPU'),gpus.length?h('option',{value:'gpu'},'GPU · '+gpus[0].name):null);
  if(gpus.length)backend.value='gpu';
  const context=h('select',{name:'context','aria-label':tr('Context size')},[2048,4096,8192,16384,32768].map(c=>h('option',{value:c},trf('{count} tokens',{count:c}))));context.value='8192';
  const threads=h('input',{name:'threads',type:'number',min:1,max:navigator.hardwareConcurrency||64,value:s.defaultThreads,'aria-label':tr('CPU threads')});
  return h('div',{class:'admin-user-row ai-installed-row'},
   h('div',{},h('strong',{},m.repository),h('small',{},`${m.filename} · ${gib(m.size)} · ${tr('SHA-256 verified')}${m.downloadedAt?' · '+m.downloadedAt.slice(0,10):''}`),
    aiHardware?fitPill(fitFor(m.size)):null,isActive?h('span',{class:'pill ok'},tr(s.engine.state==='ready'?'Active':'Loading')):null),
   isActive?h('div',{class:'actions'},btn('Unload',async()=>{await api('/admin/ai/unload','POST',{});await refreshAi()})):
   h('div',{class:'ai-activate'},h('label',{},h('span',{},tr('Device')),backend),h('label',{},h('span',{},tr('Context')),context),h('label',{},h('span',{},tr('Threads')),threads),
    btn('Activate',async()=>{await api('/admin/ai/activate','POST',{id:m.id,backend:backend.value,context:context.value,threads:threads.value});aiInstalledSignature='';await refreshAi()},'primary'),
    btn('Delete',async()=>{if(!confirm(trf('Delete {file} ({size}) from this server? The file is removed from disk.',{file:m.filename,size:gib(m.size)})))return;await api('/admin/ai/models/'+m.id,'DELETE');aiInvalidate();await refreshAi();notice('Model deleted.')},'danger')))})}
function fitFor(size){const hw=aiHardware;const need=size*1.15+600*1048576;if(hw.gpus.length&&need<=hw.gpuBudgetBytes)return 'gpu';if(need<=hw.cpuBudgetBytes)return 'cpu';if(hw.gpus.length&&need<=hw.gpuBudgetBytes+hw.cpuBudgetBytes)return 'partial';return 'too_large'}
async function startDownload(repository,filename){await api('/admin/ai/download','POST',{repository,filename});if($('#dialog').open)$('#dialog').close();await refreshAi();notice('Download started.')}
function recommendedView(){
 const list=h('div',{},h('p',{class:'muted'},tr('Loading recommendations from Hugging Face…')));
 const fill=rows=>list.replaceChildren(h('p',{class:'muted'},tr('Instruction-tuned models checked with the bundled llama.cpp engine. The highlighted model is the best match for this server. Sizes and popularity come from Hugging Face.')),...rows.map(r=>h('div',{class:'ai-card'+(r.recommended?' recommended':'')},
  h('div',{class:'ai-card-main'},h('div',{},h('strong',{},r.title),' ',h('span',{class:'pill'},r.parameters),' ',r.recommended?h('span',{class:'pill ok'},tr('Recommended for this server')):null),
   h('small',{},`${r.repository} · ${r.quantization||''} · ${gib(r.size)} · ${trf('{count} downloads',{count:num(r.downloads)})} · ${trf('{count} likes',{count:num(r.likes)})}${r.license?' · '+tr('license')+': '+r.license:''}`),
   h('p',{},tr(r.note)),r.error?h('small',{class:'error-text'},aiText(r.error)):fitPill(r.fit)),
  h('div',{class:'actions'},r.installed?h('span',{class:'pill ok'},tr('Installed')):r.available&&r.fit!=='too_large'?h('button',{type:'button',class:r.recommended?'primary':'',onclick:()=>perform(()=>startDownload(r.repository,r.filename))},trf('Download {quant} · {size}',{quant:r.quantization,size:gib(r.size)})):null,btn('Other quantizations',()=>showRepository(r.repository))))));
 if(aiRecommendations)fill(aiRecommendations);else queueMicrotask(()=>perform(async()=>{try{aiRecommendations=await api('/admin/ai/recommendations');if(list.isConnected)fill(aiRecommendations)}catch(e){list.replaceChildren(h('p',{class:'error-text'},aiText(e.message)));throw e}}));
 return h('section',{class:'panel admin-panel'},h('h2',{},tr('Recommended')),list)}
function repoList(list){return list.length?list.map(m=>h('div',{class:'admin-user-row'},h('div',{},h('strong',{},m.id),h('small',{},`${trf('{count} downloads',{count:num(m.downloads)})} · ${trf('{count} likes',{count:num(m.likes)})}${m.updatedAt?' · '+tr('updated')+' '+String(m.updatedAt).slice(0,10):''}${m.gated?' · '+tr('gated'):''}`)),btn('View quantizations',()=>showRepository(m.id)))):[h('p',{class:'muted'},tr('No matching GGUF repositories.'))]}
function popularView(){
 const list=h('div',{},h('p',{class:'muted'},tr('Loading…')));
 const fill=rows=>list.replaceChildren(...repoList(rows));
 if(aiPopular)fill(aiPopular);else queueMicrotask(()=>perform(async()=>{try{aiPopular=await api('/admin/ai/search?popular=1');if(list.isConnected)fill(aiPopular)}catch(e){list.replaceChildren(h('p',{class:'error-text'},aiText(e.message)));throw e}}));
 return h('section',{class:'panel admin-panel'},h('h2',{},tr('Popular')),h('p',{class:'muted'},tr('Most downloaded GGUF text-generation repositories on Hugging Face. Popularity does not guarantee compatibility or quality; prefer instruction-tuned (Instruct, -it) models.')),list)}
function searchView(){
 const results=h('div',{id:'ai-results','aria-live':'polite'});
 const form=submit(h('form',{class:'ai-search-form'},field('Search Hugging Face',input('query','text',{required:true,maxlength:100,placeholder:'Qwen3 instruct'})),h('button',{type:'submit',class:'primary'},tr('Search'))),async d=>{results.replaceChildren(h('p',{},tr('Searching…')));try{results.replaceChildren(...repoList(await api('/admin/ai/search?q='+encodeURIComponent(d.get('query')))))}catch(e){results.replaceChildren(h('p',{class:'error-text'},aiText(e.message)))}});
 const repo=submit(h('form',{class:'ai-repository-form'},field('Repository URL or owner/name',input('repo','text',{required:true,maxlength:200,placeholder:'Qwen/Qwen2.5-1.5B-Instruct-GGUF'})),h('button',{type:'submit'},tr('View quantizations'))),async d=>showRepository(d.get('repo')));
 return h('section',{class:'panel admin-panel'},h('h2',{},tr('Search')),h('p',{class:'muted'},tr('Results are sorted by downloads. Searching sends only your search text to Hugging Face; no investigation data leaves the server.')),form,results,h('h3',{},tr('Open a repository')),repo)}
function installedView(){aiInstalledSignature='';const holder=h('div',{id:'ai-installed'},h('p',{class:'muted'},tr('Loading…')));queueMicrotask(()=>perform(refreshAi));
 return h('section',{class:'panel admin-panel'},h('h2',{},tr('Installed')),h('p',{class:'muted'},tr('Models are stored in the server data folder (data/models). GPU uses the Vulkan build of llama.cpp and keeps layers that do not fit in GPU memory on the CPU. Larger context sizes allow more findings per analysis but need more memory.')),h('p',{class:'muted',id:'ai-storage'}),holder)}
// ---- Cloud and API providers ----
let aiCloud=null;
function cloudView(){
 const holder=h('div',{},h('p',{class:'muted'},tr('Loading…')));
 async function load(){aiCloud=await api('/admin/ai/cloud');if(holder.isConnected)fill()}
 function fill(){
  const c=aiCloud;
  holder.replaceChildren(
   h('p',{class:'help'},tr('Cloud providers receive the findings a user selects for analysis. Users must confirm this for every cloud analysis, and each one is recorded in the activity log. Local servers (Ollama, LM Studio, LiteLLM) keep data on your network. API keys are stored encrypted on this server and are never shown again.')),
   h('h3',{},tr('Configured providers')),
   ...(c.providers.length?c.providers.map(p=>h('div',{class:'admin-user-row ai-installed-row'},
    h('div',{},h('strong',{},p.name),' ',pill(p.local?tr('Local server'):tr('Cloud'),p.local?'ok':'warn'),' ',pill(tr(p.enabled?'Enabled':'Disabled'),p.enabled?'ok':''),h('small',{},`${p.baseUrl} · ${p.model} · ${p.hasKey?tr('Key configured'):tr('No key')}`)),
    h('div',{class:'actions'},btn('Test',async()=>{notice('Testing…');await api(`/admin/ai/cloud/${p.id}/test`,'POST',{});notice('The provider answered correctly.')}),
     btn('Edit',()=>cloudDialog(c.presets.find(x=>x.id===p.preset)||c.presets.at(-1),p)),
     btn(p.enabled?'Disable':'Enable',async()=>{await api(`/admin/ai/cloud/${p.id}`,'PATCH',{enabled:!p.enabled});await load()}),
     btn('Delete',async()=>{if(!confirm(trf('Delete {name}? Its stored API key is removed.',{name:p.name})))return;await api(`/admin/ai/cloud/${p.id}`,'DELETE');await load()},'danger')))):[h('p',{class:'muted'},tr('No providers yet. Add one from the list below.'))]),
   h('h3',{},tr('Add a free AI API')),h('p',{class:'muted'},tr('From the free-ai-apis list (github.com/OuterSpacee/free-ai-apis). All use the OpenAI-compatible chat API. Free tiers and model names change; check the provider before use.')),
   h('div',{class:'cloud-presets'},c.presets.map(pr=>h('div',{class:'cloud-preset'},h('strong',{},pr.name),h('small',{},tr(pr.freeTier)||'—'),pr.local?pill(tr('Local server'),'ok'):null,h('div',{class:'actions'},btn('Add',()=>cloudDialog(pr)),pr.signupUrl?h('a',{href:pr.signupUrl,target:'_blank',rel:'noopener noreferrer'},tr('Get API key ↗')):null)))))}
 function cloudDialog(pr,existing){
  const models=h('datalist',{id:'cloud-models'});
  const f=submit(h('form',{class:'form-grid'},
   field('Name',input('name','text',{maxlength:80,value:existing?.name||pr.name,disabled:!!existing})),
   field('API base URL',input('baseUrl','url',{required:true,maxlength:300,value:existing?.baseUrl||pr.baseUrl,disabled:!!existing})),
   field(existing?'New API key (leave empty to keep the current key)':pr.keyRequired?'API key':'API key (optional)',input('apiKey','password',{maxlength:4096,autocomplete:'off',required:!existing&&pr.keyRequired})),
   field('Model',input('model','text',{required:true,maxlength:200,value:existing?.model||pr.model,list:'cloud-models'})),models,
   field('Context window (tokens)',input('contextTokens','number',{min:2048,max:1000000,value:existing?.contextTokens||32768})),
   h('div',{class:'actions'},btn('Fetch models',async()=>{const fd=new FormData(f);const list=await api('/admin/ai/cloud/models','POST',{baseUrl:fd.get('baseUrl')||existing?.baseUrl,apiKey:fd.get('apiKey')||'',id:existing?.id||''});models.replaceChildren(...list.map(m=>h('option',{value:m})));notice(trf('{count} models found.',{count:list.length}))}),
    h('button',{type:'submit',class:'primary'},tr('Save'))),
   pr.signupUrl?h('p',{},h('a',{href:pr.signupUrl,target:'_blank',rel:'noopener noreferrer'},tr('Get API key ↗'))):null),async d=>{
    if(existing)await api(`/admin/ai/cloud/${existing.id}`,'PATCH',{model:d.get('model'),apiKey:d.get('apiKey'),contextTokens:d.get('contextTokens')});
    else await api('/admin/ai/cloud','POST',{preset:pr.id,name:d.get('name'),baseUrl:d.get('baseUrl'),model:d.get('model'),apiKey:d.get('apiKey'),contextTokens:d.get('contextTokens')});
    $('#dialog').close();await load();notice('Saved.')});
  modal(existing?existing.name:pr.name,f)}
 queueMicrotask(()=>perform(load));
 return h('section',{class:'panel admin-panel'},h('h2',{},tr('Cloud & API')),holder)}
function aiView(){
 const tabs=[['recommended','Recommended'],['popular','Popular'],['search','Search'],['installed','Installed'],['cloud','Cloud & API']];
 const body={recommended:recommendedView,popular:popularView,search:searchView,installed:installedView,cloud:cloudView}[aiTab]||recommendedView;
 queueMicrotask(()=>perform(refreshAi));
 return h('div',{},h('div',{class:'admin-page-head'},h('div',{},h('h1',{},tr('AI Models')),h('p',{},tr('Run open models locally with llama.cpp (offline after download), or connect cloud and API providers.')))),
  hardwareView(),h('div',{id:'ai-engine'}),h('div',{id:'ai-download','aria-live':'polite'}),
  h('div',{class:'tabs ai-tabs',role:'tablist'},tabs.map(([id,label])=>h('button',{type:'button',role:'tab','aria-selected':String(aiTab===id),class:aiTab===id?'selected':'',onclick:()=>aiNav(id)},tr(label)))),body())}
async function showRepository(id){
 const info=await api('/admin/ai/repository?id='+encodeURIComponent(id));
 const rows=info.files.map(f=>h('tr',{},h('td',{},h('strong',{},f.quantization||'—')),h('td',{class:'ai-file'},f.filename),h('td',{},gib(f.size)),h('td',{},fitPill(f.fit)),
  h('td',{},f.installed?h('span',{class:'pill ok'},tr('Installed')):f.downloadable?btn('Download',()=>startDownload(info.repository,f.filename),f.fit==='too_large'?'':'primary'):h('small',{},tr(info.gated?'Gated repository':f.filename.includes('/')||/-\d{5}-of-\d{5}\.gguf$/i.test(f.filename)?'Split or nested files are not supported':/^mmproj/i.test(f.filename)?'Vision projector, not a model':'No SHA-256 published')))));
 modal('Choose a quantization',h('div',{class:'ai-quant'},h('strong',{},info.repository),h('p',{class:'muted'},`${trf('{count} downloads',{count:num(info.downloads)})} · ${trf('{count} likes',{count:num(info.likes)})} · ${tr('license')}: ${info.license||tr('see model card')} · ${tr('revision')} ${info.revision.slice(0,10)}`),
  h('p',{class:'help'},tr('Q4_K_M is a good balance of size and quality. Higher numbers (Q5, Q6, Q8) are larger and more accurate; lower (Q2, Q3, IQ) are smaller and less accurate. Each download is verified with the SHA-256 published by Hugging Face. Review the model card and license before use.')),
  h('p',{},h('a',{href:info.modelCard,target:'_blank',rel:'noopener noreferrer'},tr('Open model card ↗'))),
  info.files.length?h('div',{class:'table-wrap'},h('table',{},h('thead',{},h('tr',{},['Quantization','File','Size','Fit',''].map(x=>h('th',{},x?tr(x):'')))),h('tbody',{},rows))):h('p',{},tr('No GGUF files found in this repository.'))))}
setInterval(()=>{if(active==='ai-models'&&me&&$('#ai-engine')&&!$('#dialog').open)perform(refreshAi)},2000);
function render(){const nav=[['users','User accounts'],['add-user','Add user'],['requests','Password reset requests'],['keys','API keys'],['logs','Activity log'],['ai-models','AI Models']];const current=nav.find(([id])=>id===active)||nav[0];const page={users:usersView,'add-user':addUserView,requests:requestsView,keys:keysView,logs:logsView,'ai-models':aiView}[active]();
 $('#app').replaceChildren(h('div',{class:'layout admin-layout'},h('aside',{class:'sidebar admin-sidebar'},h('div',{class:'brand'},h('span',{},'O'),'OSINT-Book'),h('div',{class:'eyebrow'},tr('Admin panel')),h('nav',{'aria-label':tr('Admin navigation'),class:'admin-nav'},nav.map(([id,label])=>h('button',{type:'button',class:active===id?'selected':'',...(active===id?{'aria-current':'page'}:{}),onclick:()=>navigate(id)},tr(label),id==='requests'&&requests.some(r=>r.status==='pending')?h('span',{class:'admin-nav-count'},requests.filter(r=>r.status==='pending').length):null))),h('div',{class:'admin-sidebar-bottom'},btn('Refresh',load),btn('↗  Sign out',async()=>{await api('/logout','POST',{});login()}))),h('main',{class:'main admin-main'},h('header',{class:'topbar admin-topbar'},h('div',{class:'admin-breadcrumb'},tr('Server administration'),h('span',{'aria-hidden':'true'},'/'),h('strong',{},tr(current[1]))),h('div',{class:'admin-top-actions'},picker(),h('div',{class:'admin-profile'},h('span',{class:'admin-profile-avatar','aria-hidden':'true'},me.username.charAt(0).toUpperCase()),h('span',{class:'admin-profile-name'},me.username)))),h('div',{class:'admin-content'},page))))}
perform(async()=>{try{await load()}catch(e){login();if(e.message!==tr('login_required'))notice(e.message)}});
