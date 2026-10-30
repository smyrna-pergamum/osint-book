'use strict';
const $ = s => document.querySelector(s);
const state = { list: [], providers: [], bundle: null, analysis: null, tab: 'targets', archive: false, search: '', translations: {}, loading: false, user: null, serverAdmin: false, users: [] };
const types = { DOMAIN:tr("Domain"), URL:'URL', IP:tr("IP address"), ORG:tr("Organization"), REPO:'GitHub', PACKAGE:tr("Package"), HASH:tr("File hash") };
const reviews = { UNREVIEWED:tr("Unreviewed"), VERIFIED:tr("Verified"), CONFLICTING:tr("Conflicting") };
const statuses = { PENDING:tr("Pending"), RUNNING:tr("Running"), SUCCESS:tr("Completed"), NO_RESULT:tr("No results"), RATE_LIMITED:tr("Rate limited"), UNAUTHORIZED:tr("Key rejected"), NO_KEY:tr("Missing key"), NETWORK:tr("Network error"), TIMEOUT:tr("Timeout"), SERVER:tr("Service error"), PARSE:tr("Invalid response"), TOO_LARGE:tr("Response too large"), CANCELLED:tr("Cancelled"), UNSUPPORTED:tr("Unsupported") };
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key,value] of Object.entries(attrs)) {
    if (key.startsWith('on')) el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'class') el.className = value;
    else if (key === 'checked' || key === 'disabled' || key === 'selected') el[key] = value;
    else if (value != null) el.setAttribute(key,value);
  }
  children.flat(Infinity).forEach(c => { if (c != null) el.append(c instanceof Node ? c : document.createTextNode(String(c))); });
  return el;
}
function btn(text, action, cls = '') { return h('button',{type:'button',class:cls,onclick:() => perform(action)},text); }
function field(label, el) { return h('label',{},label,el); }
function input(name, placeholder = '', value = '') { return h('input',{name,placeholder,value}); }
function select(name, options, current) { return h('select',{name},Object.entries(options).map(([value,text]) => h('option',{value,selected:value === current},text))); }
function pill(text, cls = '') { return h('span',{class:`pill ${cls}`},text); }
function date(value) { return value ? new Date(value).toLocaleString(language,{dateStyle:'medium',timeStyle:'short'}) : '—'; }
function provider(id) { return state.providers.find(p => p.id === id)?.name || id; }
function target(id) { return state.bundle?.targets.find(t => t.id === id)?.displayValue || tr("General"); }
function link(url, title) {
  try { const u = new URL(url); if (!['https:','http:'].includes(u.protocol)) throw Error(); return h('a',{href:u.href,target:'_blank',rel:'noopener noreferrer'},title || u.hostname); }
  catch { return h('span',{class:'muted'},title || url || tr("No source link")); }
}
let noticeTimer;
function notice(text) { $('#notice').textContent = tr(text); clearTimeout(noticeTimer); noticeTimer = setTimeout(() => $('#notice').textContent = '',8000); }
async function perform(action) { try { return await action(); } catch(e) { notice(e.message || tr("Operation failed.")); } }
async function trackedFetch(url, options = {}, label = tr('Requests'), mode = 'json', quiet = false) {
  const controller = new AbortController();
  const panel = h('div',{class:'operation'},h('span',{},label),btn(tr('Cancel request'),()=>controller.abort()));
  panel.abortRequest=()=>controller.abort();
  if(!quiet) ($('#dialog').open ? $('#dialog .dialog-body') : $('#operations')).append(panel);
  try {
    const response=await fetch(url,{...options,signal:controller.signal});
    if(!response.ok) {
      const result=await response.json().catch(()=>({error:'operation_failed'}));
      if(response.status===401 && !url.endsWith('/login')) login();
      const error=Error(tr(result.error||'operation_failed')+(result.detail?' — '+result.detail:''));error.status=response.status;throw error;
    }
    return mode==='blob' ? await response.blob() : await response.json();
  } catch(e) {
    if(e.name==='AbortError') throw Error(tr('Request cancelled. Completed saves are not undone; refresh to check.'));
    throw e;
  } finally {panel.remove();}
}
async function api(path, method = 'GET', data) {
  const headers={'X-OSINT-Request':'1'};
  if(data!==undefined) headers['Content-Type']='application/json';
  return trackedFetch(`/api${path}`,{method,headers,body:data===undefined?undefined:JSON.stringify(data)},tr('Requests'),'json',state.loading||path.endsWith('/cancel'));
}
function languagePicker() {
  const chooser=select('language',languages,language);
  chooser.setAttribute('aria-label',tr('Language'));
  chooser.onchange=()=>{try{localStorage.setItem('osint-language',chooser.value)}catch{}location.reload();};
  return h('label',{class:'language'},tr('Language'),chooser);
}
async function downloadReport(format) {
  const blob=await trackedFetch('/api'+base(`/export/${format}`),{},tr(format==='json'?'Download JSON':'Download CSV'),'blob');
  const url=URL.createObjectURL(blob), a=h('a',{href:url,download:`osint-book-${state.bundle.investigation.id}.${format}`});
  document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);
}
function base(suffix = '') { return `/investigations/${state.bundle.investigation.id}${suffix}`; }
function modal(title, body, buttons = []) {
  const d = $('#dialog');
  d.onclose=()=>d.querySelectorAll('.operation').forEach(p=>p.abortRequest?.());
  d.replaceChildren(h('div',{class:'dialog-head'},h('h2',{},title),btn('×',() => d.close())),h('div',{class:'dialog-body'},body),h('div',{class:'dialog-foot'},btn(tr('Cancel'),()=>d.close()),buttons));
  if (!d.open) d.showModal();
  return d;
}
function formSubmit(form, action) {
  form.addEventListener('submit',async e => {
    e.preventDefault(); const button = form.querySelector('[type=submit]'); if (button) button.disabled = true;
    try { await action(new FormData(form)); } catch(e) { notice(e.message); }
    finally { if (button) button.disabled = false; }
  });
  return form;
}
function login() {
  state.bundle = null; state.list = [];state.user=null;state.serverAdmin=false;$('#dialog').close();
  const password = h('input',{type:'password',name:'password',required:true,autocomplete:'current-password',placeholder:tr('Password')});
  const form = formSubmit(h('form',{class:'form-grid'},field(tr('Username'),h('input',{name:'username',required:true,autocomplete:'username',maxlength:40})),field(tr("Password"),password),h('button',{type:'submit',class:'primary'},tr("Sign in →"))),async data => {
    await api('/login','POST',{username:data.get('username'),password:data.get('password')}); await load();
  });
  const reset=btn(tr('Request password reset'),()=>{const f=formSubmit(h('form',{class:'form-grid'},field(tr('Username'),h('input',{name:'username',required:true,minlength:3,maxlength:40,pattern:'[a-zA-Z0-9_.-]{3,40}'})),h('button',{type:'submit',class:'primary'},tr('Send request'))),async data=>{await api('/password-reset-request','POST',{username:data.get('username')});$('#dialog').close();notice(tr('If the account is eligible, the administrator will see your request.'));});modal(tr('Request password reset'),f);});
  $('#app').replaceChildren(h('div',{class:'login'},
    h('section',{class:'login-story'},h('div',{},brand(),h('div',{class:'eyebrow'},tr("Open-source intelligence"))),h('div',{},h('h1',{},tr("Collect findings. Discover connections.")),h('p',{},tr("Traceable evidence and a shared investigation workspace."))),h('small',{},'OSINT-Book / Windows & Web · 0.2.1')),
    h('section',{class:'login-form'},languagePicker(),pill(tr("LOCAL WORKSPACE"),'ok'),h('h2',{},tr("Continue your research")),h('p',{class:'muted'},tr("Sign in to your Windows server.")),form,reset,h('small',{},tr('Your server administrator creates your account.')))));
}
function brand() { return h('div',{class:'brand'},h('span',{},'O'), 'OSINT-Book'); }
async function load() {
  [state.providers,state.list,state.user] = await Promise.all([api('/providers'),api('/investigations'),api('/me')]);
  state.translations = await fetch(`/analysis/${language}.json`).then(r => r.json());
  const id = location.hash.slice(1);
  if (id && state.list.some(b => b.investigation.id === id)) await openInvestigation(id);
  else { state.bundle = null; render(); }
}
async function refresh(renderPage = true) {
  const id = state.bundle?.investigation.id;
  state.list = await api('/investigations');
  if (id) [state.bundle,state.analysis] = await Promise.all([api(`/investigations/${id}`),api(`/investigations/${id}/analysis`)]);
  if (renderPage) render();
}
async function openInvestigation(id) {
  [state.bundle,state.analysis] = await Promise.all([api(`/investigations/${id}`),api(`/investigations/${id}/analysis`)]);
  state.serverAdmin = false; history.replaceState(null,'',`#${id}`); state.tab = 'targets'; render();
}
function home() { state.serverAdmin = false; state.bundle = null; history.replaceState(null,'',location.pathname); render(); }
function render() {
  const side = h('aside',{class:'sidebar'},brand(),h('div',{class:'eyebrow'},tr("Research workspace")),
    btn(tr("▦  All investigations"),home,!state.bundle?'selected':''),btn(tr("+  New investigation"),createInvestigation,'new'),
    h('div',{class:'side-title'},tr("RECENT INVESTIGATIONS")),h('div',{class:'side-list'},state.list.filter(b => !b.investigation.archivedAt).slice(0,15).map(b => btn(b.investigation.title,() => openInvestigation(b.investigation.id),state.bundle?.investigation.id === b.investigation.id?'selected':''))),
    btn(tr("⚙  Settings"),settings),btn(tr("↗  Sign out"),async () => { await api('/logout','POST',{}); login(); }),
    h('div',{class:'server'},h('div',{},h('span',{class:'dot'},'●'),tr(" Windows server")),h('div',{},location.host),h('small',{},tr("Data is stored on the server."))));
  const main = h('main',{class:'main'},h('header',{class:'topbar'},h('span',{},tr("Workspace"), '  /  ',h('strong',{},state.bundle?tr("Investigation"):tr("Overview"))),h('div',{class:'actions'},languagePicker(),h('span',{},state.user?.username||''),btn(tr("Refresh"),()=>refresh()),pill(tr("●  Shared local database")))),
    state.serverAdmin ? serverManagementView() : state.bundle ? investigationView() : dashboard());
  $('#app').replaceChildren(h('div',{class:'layout'},side,main));
}
function dashboard() {
  const visible = state.list.filter(b => Boolean(b.investigation.archivedAt) === state.archive && `${b.investigation.title} ${b.investigation.description}`.toLocaleLowerCase(language).includes(state.search.toLocaleLowerCase(language)));
  const search = input('search',tr("Search investigations…"),state.search); search.className='search';
  search.addEventListener('input',e => { state.search = e.target.value; const p=e.target.selectionStart; render(); const n=$('[name=search]'); n.focus(); n.setSelectionRange(p,p); });
  return h('div',{},h('section',{class:'page-head'},h('div',{},h('h1',{},tr("Your research, together.")),h('p',{},tr("Collect public information, review findings and report with sources."))),h('div',{class:'actions'},btn(tr("Import JSON"),importDialog),btn(tr("+ New investigation"),createInvestigation,'primary'))),
    h('section',{class:'stats'},stat(tr("Active investigations"),state.list.filter(b=>!b.investigation.archivedAt).length,tr("In the shared workspace")),stat(tr("Research targets"),state.list.reduce((n,b)=>n+b.targets,0),tr("Domains, IPs and more")),stat(tr("Saved findings"),state.list.reduce((n,b)=>n+b.findings,0),tr("With source and retrieval time"))),
    h('div',{class:'section-line'},h('div',{class:'actions'},h('h2',{},state.archive?tr("Archive"):tr("Investigation files")),btn(state.archive?tr("Show active"):tr("Show archive"),()=>{state.archive=!state.archive;render();})),search),
    visible.length ? h('div',{class:'cards'},visible.map(b=>h('article',{class:'card'},h('div',{class:'card-top'},h('div',{class:'icon-box'},'⌕'),pill(b.investigation.archivedAt?tr("Archive"):tr("Active"),b.investigation.archivedAt?'':'ok')),h('h3',{},b.investigation.title),h('p',{class:'muted'},b.investigation.description || tr("Add targets and choose sources to begin.")),h('div',{class:'card-meta'},h('span',{},`${b.targets} ${tr("targets")}`),h('span',{},`${b.findings} ${tr("findings")}`)),h('footer',{},h('small',{},date(b.investigation.updatedAt)),h('div',{class:'actions'},btn(tr("Delete"),()=>deleteInvestigationDialog(b.investigation),'danger'),btn(tr("Open →"),()=>openInvestigation(b.investigation.id),'link-button')))))) : empty(tr("No investigations yet"),tr("Create an investigation or import an Android JSON export.")),
    h('p',{class:'footer-note'},tr("Automatic analysis does not replace manual verification.")));
}
function stat(label,value,detail) { return h('div',{class:'stat'},h('span',{},label),h('strong',{},value),h('span',{},detail)); }
function empty(title,detail) { return h('div',{class:'empty'},h('h2',{},title),h('p',{},detail)); }
function createInvestigation() {
  const form = h('form',{class:'form-grid'},field(tr("Investigation name"),h('input',{name:'title',required:true,maxlength:300,placeholder:tr("Example: example.org infrastructure")})),field(tr("Description"),h('textarea',{name:'description',maxlength:20000,placeholder:tr("Purpose, scope and research notes")})),h('button',{class:'primary',type:'submit'},tr("Create investigation")));
  const d=modal(tr("New investigation"),form);
  formSubmit(form,async data=>{const b=await api('/investigations','POST',Object.fromEntries(data));d.close();state.list=await api('/investigations');await openInvestigation(b.investigation.id);});
}
function importDialog() {
  const file = h('input',{type:'file',accept:'.json,application/json',required:true});
  const form = h('form',{class:'form-grid'},h('p',{},tr("Android and web JSON files are supported. Imported records receive new IDs. Limit: 20 MiB.")),file,h('button',{type:'submit',class:'primary'},tr("Import")));
  const d=modal(tr("Import investigation"),form);
  formSubmit(form,async()=>{
    const f=file.files[0];if(!f)throw Error(tr("Choose a file."));if(f.size>20*1024*1024)throw Error(tr("File exceeds 20 MiB."));
    const b=await trackedFetch('/api/import',{method:'POST',headers:{'X-OSINT-Request':'1','Content-Type':'application/json'},body:await f.text()},tr('Import')); d.close();state.list=await api('/investigations');await openInvestigation(b.investigation.id);notice(tr("Investigation imported."));
  });
}
function investigationView() {
  const b=state.bundle, inv=b.investigation;
  const tabs={targets:`${tr("Targets")} · ${b.targets.length}`,findings:`${tr("Findings")} · ${b.findings.length}`,analysis:tr("Analysis"),map:tr('Map'),notes:tr("Notes & relations"),report:tr("Prepare report")};
  const page = state.tab==='targets'?targetsView():state.tab==='findings'?findingsView():state.tab==='analysis'?analysisView():state.tab==='map'?mapView():state.tab==='notes'?notesView():reportView();
  return h('div',{},h('div',{class:'page-head'},h('div',{},h('h1',{},inv.title),h('p',{},inv.description || tr("Choose sources to extend your research.")),inv.archivedAt?pill(tr("Archived investigation"),'warn'):null),h('div',{class:'actions'},btn(tr("Edit"),editInvestigation),btn(tr("Prepare report"),()=>{state.tab='report';render();},'primary'))),h('nav',{class:'tabs','aria-label':tr("Investigation sections")},Object.entries(tabs).map(([id,label])=>btn(label,()=>{state.tab=id;render();},state.tab===id?'selected':''))),page);
}
function editInvestigation() {
  const inv=state.bundle.investigation;
  const form=h('form',{class:'form-grid'},field(tr("Title"),h('input',{name:'title',value:inv.title,required:true,maxlength:300})),field(tr("Description"),h('textarea',{name:'description',maxlength:20000},inv.description)),h('button',{type:'submit',class:'primary'},tr("Save")));
  const d=modal(tr("Edit investigation"),form,[btn(inv.archivedAt?tr("Unarchive"):tr("Archive investigation"),async()=>{await api(base(),'PATCH',{archived:!inv.archivedAt});d.close();await refresh();}),btn(tr("Delete"),()=>deleteInvestigationDialog(inv),'danger')]);
  formSubmit(form,async data=>{await api(base(),'PATCH',Object.fromEntries(data));d.close();await refresh();});
}
function latestRuns(t) {
  const latest=new Map();state.bundle.queryRuns.filter(r=>r.targetId===t.id).forEach(r=>latest.set(r.providerId,r));return [...latest.values()];
}
function targetsView() {
  const form=h('form',{class:'target-form'},field(tr("Target type"),select('type',{'':tr("Detect automatically"),...types})),field(tr("Target"),h('input',{name:'value',required:true,maxlength:2048,placeholder:'example.org, 8.8.8.8, owner/repo, npm:package@1.0.0…'})),h('button',{type:'submit',class:'primary'},tr("+ Add target")),h('button',{type:'reset'},tr("Cancel")));
  formSubmit(form,async data=>{const result=await api(base('/targets'),'POST',Object.fromEntries(data));await refresh();if(result.warnings.length)notice(tr("Target warnings: ")+result.warnings.map(w=>({SECRET_PARAMS:tr("Secret URL parameters removed"),USERINFO:tr("URL user information removed"),PRIVATE_IP:tr("Private IP address"),NO_VERSION:tr("No package version specified")}[w]||w)).join(' · '));});
  return h('div',{},h('section',{class:'panel'},h('h2',{},tr("Investigation targets")),h('p',{class:'muted'},tr("Add a target, then review the selected sources before querying.")),state.bundle.investigation.archivedAt?null:form),
    state.bundle.targets.length?h('section',{class:'panel'},btn(tr('Cancel all queries'),async()=>{await api(base('/cancel'),'POST',{});await refresh();}),state.bundle.targets.map(t=>{
      const runs=latestRuns(t), running=runs.some(r=>['PENDING','RUNNING'].includes(r.status));
      return h('div',{class:'target'},h('div',{},pill(types[t.type]),h('strong',{},t.displayValue),h('small',{},`${state.bundle.findings.filter(f=>f.targetId===t.id).length} ${tr("findings")}`),h('div',{class:'runs'},runs.map(r=>h('span',{title:`${provider(r.providerId)}: ${r.errorCode||''} · ${r.requestCount} ${tr("requests")}`},pill(`${provider(r.providerId)} · ${statuses[r.status]||r.status}`,r.status==='SUCCESS'?'ok':['PENDING','RUNNING','NO_RESULT'].includes(r.status)?'':'warn'),['PENDING','RUNNING'].includes(r.status)?btn(tr('Cancel operation'),async()=>{await api(base('/cancel'),'POST',{targetId:t.id,providerId:r.providerId});await refresh();}):null)))),h('div',{class:'actions'},running?btn(tr("Cancel operation"),async()=>{await api(base('/cancel'),'POST',{targetId:t.id});await refresh();}):!state.bundle.investigation.archivedAt?btn(tr("Query sources →"),()=>queryDialog(t),'primary'):null,!state.bundle.investigation.archivedAt?btn(tr('Delete target'),()=>deleteTargetDialog(t),'danger'):null));
    })):empty(tr("Add your first target"),tr("Start with a domain, URL, IP, organization, repository, package or hash.")));
}
function deleteTargetDialog(t) {
  const count=state.bundle.findings.filter(f=>f.targetId===t.id).length;
  modal(tr('Delete this target?'),h('div',{},h('strong',{},t.displayValue),h('p',{},tr('This permanently deletes the target, its query history, findings, evidence, linked notes, tags and relations.'),' ',`${count} ${tr('findings')}`),h('p',{},tr('Cancel any running queries and wait for them to finish first.'))),[btn(tr('Delete target'),async()=>{await api(base(`/targets/${encodeURIComponent(t.id)}`),'DELETE');$('#dialog').close();await refresh();},'danger')]);
}
async function openServerManagement() {
  state.users=await api('/admin/users'); state.serverAdmin=true; state.bundle=null;
  history.replaceState(null,'','#server-admin');render();
}
function serverManagementView() {
  const form=formSubmit(h('form',{class:'form-grid'},field(tr('Username'),h('input',{name:'username',required:true,minlength:3,maxlength:40,pattern:'[a-zA-Z0-9_.-]{3,40}',autocomplete:'off'})),field(tr('New password'),h('input',{name:'password',type:'password',required:true,minlength:12,maxlength:256,autocomplete:'new-password'})),h('label',{class:'actions'},h('input',{type:'checkbox',name:'admin'}),tr('Administrator')),h('div',{class:'actions'},h('button',{type:'submit',class:'primary'},tr('Add user')),h('button',{type:'reset'},tr('Cancel')))),async data=>{await api('/admin/users','POST',{username:data.get('username'),password:data.get('password'),admin:data.has('admin')});notice(tr('Saved.'));await openServerManagement();});
  return h('div',{},h('section',{class:'page-head'},h('div',{},h('h1',{},tr('Server management')),h('p',{},tr('Manage accounts on this server. All users share the investigation workspace.')))),h('section',{class:'panel'},h('h2',{},tr('User accounts')),state.users.map(u=>h('div',{class:'target'},h('div',{},h('strong',{},u.username),pill(u.admin?tr('Administrator'):tr('User')),' ',pill(u.enabled?tr('Enabled'):tr('Disabled'),u.enabled?'ok':'')),h('div',{class:'actions'},btn(tr('Reset password'),()=>resetPasswordDialog(u)),u.username!==state.user.username?btn(u.enabled?tr('Disable'):tr('Enable'),async()=>{await api('/admin/users/'+encodeURIComponent(u.username),'PATCH',{enabled:!u.enabled});await openServerManagement();}):null,u.username!==state.user.username?btn(u.admin?tr('Remove administrator'):tr('Make administrator'),async()=>{await api('/admin/users/'+encodeURIComponent(u.username),'PATCH',{admin:!u.admin});await openServerManagement();}):null)))),h('section',{class:'panel'},h('h2',{},tr('Add user')),form));
}
function queryDialog(t) {
  const available=state.providers.filter(p=>p.supports.includes(t.type)&&!p.manualOnly);
  const choices=h('div',{},available.map(p=>h('label',{class:'source'},h('input',{type:'checkbox',name:'provider',value:p.id,checked:!p.active&&(p.keyNeed==='NONE'||p.hasKey),disabled:p.keyNeed==='REQUIRED'&&!p.hasKey}),h('span',{},p.name,h('small',{},p.host,' · ',p.keyNeed==='REQUIRED'&&!p.hasKey?tr("API key missing on server"):p.hasKey?tr("Key configured"):tr("No key required"))))));
  const activeConsent=h('input',{type:'checkbox',name:'confirmActive'});
  const form=h('form',{},h('div',{class:'help'},tr("Target to send: "),h('strong',{},t.normalizedValue),h('br'),tr("Selected services receive this target and the server public IP. Some URL providers only receive the domain.")),choices,available.some(p=>p.active)?h('div',{class:'help'},tr('Active check: ping sends four packets directly from the server to the target.'),h('label',{class:'actions'},activeConsent,tr('I authorize the selected active ping check.'))):null,h('div',{class:'dialog-foot'},h('button',{type:'submit',class:'primary'},tr("Query selected sources"))));
  const d=modal(tr("Choose sources"),form);
  formSubmit(form,async()=>{const ids=[...form.querySelectorAll('input[name=provider]:checked')].map(e=>e.value);if(!ids.length)throw Error(tr("Select at least one source."));if(ids.includes('ping')&&!activeConsent.checked)throw Error(tr('Confirm the active ping check.'));await api(base('/query'),'POST',{targetId:t.id,providers:ids,confirmActive:activeConsent.checked,language});d.close();await refresh();});
}
function findingsView() {
  let q='',review='',favorites=false;
  const holder=h('div');
  function show() {const fs=state.bundle.findings.filter(f=>(!review||f.reviewStatus===review)&&(!favorites||f.favorite)&&`${f.label} ${f.value} ${provider(f.providerId)} ${state.bundle.tags.filter(t=>t.findingId===f.id).map(t=>t.label).join(' ')}`.toLocaleLowerCase(language).includes(q.toLocaleLowerCase(language)));holder.replaceChildren(fs.length?findingTable(fs):empty(tr("No matching findings"),tr("Run a query or change your filters.")));}
  const search=input('findingSearch',tr("Search findings, sources and tags…"));search.oninput=e=>{q=e.target.value;show();};
  const filter=select('review',{'':tr("All review states"),...reviews});filter.onchange=e=>{review=e.target.value;show();};
  const fav=h('input',{type:'checkbox',onchange:e=>{favorites=e.target.checked;show();}});
  show();return h('div',{},h('div',{class:'toolbar'},search,filter,h('label',{class:'actions'},fav,tr("★ Favorites"))),holder);
}
function findingTable(fs, report=false) {
  const rows=fs.map(f=>h('tr',{},
    h('td',{},h('strong',{},f.label),h('div',{class:'value'},f.value),state.bundle.tags.filter(t=>t.findingId===f.id).map(t=>pill(t.label))),
    h('td',{},h('strong',{},provider(f.providerId)),h('div',{},target(f.targetId)),link(f.sourceUrl,tr("View source ↗"))),
    h('td',{},date(f.retrievedAt),f.publishedAt?h('small',{},h('br'),tr("Published: "),date(f.publishedAt)):null),
    h('td',{},pill(reviews[f.reviewStatus],f.reviewStatus==='VERIFIED'?'ok':f.reviewStatus==='CONFLICTING'?'warn':''),h('div',{class:'actions'},f.favorite?'★':null,report?null:btn(tr("Review"),()=>findingDialog(f))))));
  return h('div',{class:'table-wrap'},h('table',{},h('thead',{},h('tr',{},[tr("Finding / Value"),tr("Source / Target"),tr("Retrieved at"),tr("Review state")].map(t=>h('th',{},t)))),h('tbody',{},rows)));
}
function findingDialog(f) {
  const ev=state.bundle.evidence.filter(e=>e.findingId===f.id);
  const form=h('form',{class:'form-grid'},h('div',{class:'evidence'},f.value),link(f.sourceUrl,tr("Open source ↗")),field(tr("Review status"),select('reviewStatus',reviews,f.reviewStatus)),h('label',{class:'actions'},h('input',{type:'checkbox',name:'favorite',checked:f.favorite}),tr("Favorite")),field(tr("Tags (comma-separated)"),input('tags','',state.bundle.tags.filter(t=>t.findingId===f.id).map(t=>t.label).join(', '))),h('button',{type:'submit',class:'primary'},tr("Save changes")),h('h3',{},tr("Evidence record")),ev.map(e=>h('div',{},h('div',{class:'evidence'},e.excerpt||tr("No source excerpt.")),h('small',{},'SHA-256: ',e.sha256,h('br'),date(e.capturedAt)))),h('small',{},tr("The checksum detects changes to the local evidence; it does not prove truth.")),state.bundle.notes.filter(n=>n.findingId===f.id).map(n=>h('div',{class:'note'},n.content,h('small',{},date(n.updatedAt)))));
  const d=modal(f.label,form,[btn(tr("Add a note to this finding"),()=>noteDialog(f.id))]);
  formSubmit(form,async data=>{await api(base(`/findings/${f.id}`),'PATCH',{reviewStatus:data.get('reviewStatus'),favorite:data.has('favorite'),tags:data.get('tags').split(',').map(s=>s.trim()).filter(Boolean)});d.close();await refresh();});
}
function observationText(o) {
  let text=state.translations[o.kind] || o.kind.replaceAll('_',' ');
  const args=[...o.args];if(o.kind==='DOMAIN_AGE')args[0]=`${args[0]} ${tr("days")}`;
  return text.replace(/%(\d+)\$[sd]/g,(_,n)=>args[Number(n)-1]??'').replaceAll('%%','%').replaceAll("\\'", "'");
}
function analysisView(report=false) {
  const observations=state.analysis?.observations||[], timeline=state.analysis?.timeline||[];
  return h('section',{class:report?'':'panel'},h('h2',{},tr("Findings analysis")),h('p',{class:'muted'},tr("Rule-based source analysis. This is not AI commentary and does not produce an overall risk score.")),observations.length?observations.map(o=>h('article',{class:`observation ${o.level==='WARN'?'warn':''}`},pill(o.level==='WARN'?tr("Warning"):o.level==='MISSING'?tr("Missing information"):tr("Information"),o.level==='WARN'?'warn':''),' ',h('small',{},target(o.targetId)),h('p',{},observationText(o)),!report&&o.evidence.length?h('div',{class:'actions'},o.evidence.slice(0,6).map(id=>{const f=state.bundle.findings.find(f=>f.id===id);return f?btn(tr("Evidence: ")+f.label,()=>findingDialog(f)):null;})):null)):empty(tr("Waiting for analysis data"),tr("Add a target and query sources.")),timeline.length?h('div',{},h('h2',{},tr("Timeline")),timeline.map(e=>h('div',{class:'note'},h('strong',{},date(e.at)),` · ${e.label}: ${e.value}`,h('small',{},`${target(e.targetId)} · ${provider(e.providerId)}`)))):null,
    report?null:aiAnalysisView());
}
function reviewPill(f){return pill(reviews[f.reviewStatus]||f.reviewStatus,f.reviewStatus==='VERIFIED'?'ok':f.reviewStatus==='CONFLICTING'?'warn':'');}
const confidenceLabel=c=>({low:tr('Low'),medium:tr('Medium'),high:tr('High')}[c]||'');
/** Read-only view of a saved AI analysis; used on the Analysis tab and on the report page. */
function aiResultView(v,b){
 const out=v.output,secs=Math.round((v.durationMs||0)/1000);
 const evidence=ids=>h('div',{class:'actions'},ids.map(id=>{const f=b.findings.find(x=>x.id===id);return f?h('button',{type:'button',class:'ai-evidence',onclick:()=>findingDialog(f)},h('span',{},`${tr('Evidence:')} ${provider(f.providerId)} · ${f.label}`),' ',reviewPill(f)):h('small',{class:'muted'},tr('Deleted finding'))}));
 const list=(title,items,render,emptyText)=>[h('h3',{},title),items.length?h('div',{},items.map(render)):h('p',{class:'muted'},emptyText)];
 const engineName=v.model?.type==='cloud'?`${v.model.repository} · ${v.model.filename}`:`${v.model?.repository||''} ${v.model?.quantization||''} · ${v.backend==='gpu'?'GPU':'CPU'}`;
 return [h('div',{class:'ai-result-meta'},pill(tr('AI interpretation'),'warn'),' ',`${engineName} · ${v.findingIds.length} ${tr('findings')}${out.parts>1?' · '+trf('{count} parts',{count:out.parts}):''} · ${secs}s · ${date(v.completedAt)}`),
  v.lastAttempt?h('p',{class:'error-text'},v.lastAttempt.state==='cancelled'?tr('The latest analysis was cancelled. Showing the previous result.'):`${tr('The latest analysis failed:')} ${tr(v.lastAttempt.error)}${v.lastAttempt.detail?' ('+v.lastAttempt.detail+')':''} ${tr('Showing the previous result.')}`):null,
  v.evidenceChanged?h('p',{class:'help'},tr('Findings used in this analysis were edited or deleted afterwards. Run the analysis again for current results.')):null,
  h('h3',{},tr('Summary')),h('p',{class:'ai-summary'},out.summary),
  ...list(tr('Evidence-linked observations'),out.observations,o=>h('div',{class:'observation'},o.title?h('strong',{},o.title):null,o.confidence?h('span',{},' ',pill(`${tr('Confidence')}: ${confidenceLabel(o.confidence)}`)):null,h('p',{},o.text),o.counterpoints?h('p',{class:'muted'},`${tr('Alternative explanations:')} ${o.counterpoints}`):null,evidence(o.findingIds)),tr('No observations.')),
  ...list(tr('Contradictions'),out.contradictions,o=>h('div',{class:'observation warn'},h('p',{},o.text),o.findingIds.length?evidence(o.findingIds):null),tr('No contradictions identified between the selected findings.')),
  ...list(tr('Missing information'),out.missingInformation,x=>h('li',{},x),tr('None reported.')),
  ...list(tr('Suggested verification'),out.verificationSteps,x=>h('li',{},x),tr('None reported.')),
  out.discardedReferences?h('p',{class:'muted'},trf('{count} unsupported reference(s) produced by the model were removed.',{count:out.discardedReferences})):null,
  h('p',{class:'footer-note'},tr('Generated by a language model from the selected findings only. It may be wrong. Original findings, evidence and review statuses were not changed.'))].filter(x=>x!=null);
}
function aiAnalysisView(){
 const b=state.bundle, output=h('div',{id:'ai-analysis-status','aria-live':'polite'},h('p',{class:'muted'},tr('Checking model status…'))), engineLine=h('div',{class:'ai-engine-line'});
 const choices=h('div',{class:'ai-choices'},b.findings.map(f=>h('label',{class:'source'},h('input',{type:'checkbox',name:'aiFinding',value:f.id}),h('span',{},h('strong',{},f.label),' · ',f.value.slice(0,180),' ',reviewPill(f),h('small',{},`${target(f.targetId)} · ${provider(f.providerId)}`)))));
 const all=v=>choices.querySelectorAll('input[name=aiFinding]').forEach(x=>x.checked=v);
 const engineSel=h('select',{name:'engine','aria-label':tr('AI model')}), outLang=select('outputLanguage',languages,language);
 const consent=h('input',{type:'checkbox',name:'confirmCloud'}), consentText=h('span'), consentBox=h('label',{class:'help ai-consent',hidden:true},consent,' ',consentText);
 let engines=null, running=false;
 const chosen=()=>{const [kind,id]=engineSel.value.split(':');return kind==='cloud'?{kind,p:engines?.cloud.find(c=>c.id===id)}:{kind,m:engines?.local.find(m=>m.id===id)};};
 function updateConsent(){const c=chosen();consentBox.hidden=!(c.kind==='cloud'&&c.p&&!c.p.local);if(!consentBox.hidden)consentText.textContent=trf('The selected findings will be sent to {name}, an external service, and processed under its terms. Continue only if this is allowed for your investigation.',{name:c.p.name});}
 engineSel.onchange=()=>{try{localStorage.setItem('osint-ai-engine',engineSel.value)}catch{}consent.checked=false;updateConsent();};
 function fillEngines(e){
  const before=engineSel.value||(()=>{try{return localStorage.getItem('osint-ai-engine')}catch{return null}})()||(e.activeModelId?'local:'+e.activeModelId:'');
  const local=e.local.map(m=>h('option',{value:'local:'+m.id},`${m.repository} · ${m.quantization||m.filename}${m.id===e.activeModelId?' · '+tr(e.state==='ready'?'loaded':'loading'):''}`));
  const cloud=e.cloud.map(p=>h('option',{value:'cloud:'+p.id},`${p.name} · ${p.model}`));
  engineSel.replaceChildren(...(local.length?[h('optgroup',{label:tr('Local models on this server (offline)')},local)]:[]),...(cloud.length?[h('optgroup',{label:tr('Cloud and API providers')},cloud)]:[]));
  if(!local.length&&!cloud.length)engineSel.append(h('option',{value:''},tr('No AI model available. Ask an administrator to add one.')));
  if([...engineSel.options].some(o=>o.value===before))engineSel.value=before;updateConsent();
 }
 const submitBtn=h('button',{type:'submit',class:'primary'},tr('Analyze with AI')), cancelBtn=btn(tr('Cancel analysis'),async()=>{await api(base('/ai-analysis/cancel'),'POST',{});await aiRefresh()});
 const form=formSubmit(h('form',{},h('div',{class:'ai-engine-pick'},field(tr('AI model'),engineSel),field(tr('Output language'),outLang)),h('p',{class:'muted ai-lang-note'},tr('Small local models (up to about 4B) write best in English. For other output languages, use a larger model or a cloud provider.'),' ',tr('If the selection is too large for the model, it is analyzed in parts automatically and the results are merged.')),consentBox,
  h('div',{class:'ai-choices-head'},h('h3',{},tr('Select findings')),h('div',{class:'actions'},btn(tr('Select all'),()=>all(true)),btn(tr('Clear'),()=>all(false)))),choices,h('div',{class:'actions'},submitBtn,cancelBtn)),async()=>{
  const ids=[...form.querySelectorAll('input[name=aiFinding]:checked')].map(x=>x.value);if(!ids.length)throw Error(tr('Select at least one finding.'));
  if(!engineSel.value)throw Error(tr('No AI model available. Ask an administrator to add one.'));
  if(!consentBox.hidden&&!consent.checked)throw Error(tr('Confirm sending the findings to the cloud provider.'));
  await api(base('/ai-analysis'),'POST',{findingIds:ids,engine:engineSel.value,language:outLang.value,confirmCloud:consent.checked});await aiRefresh()});
 async function aiRefresh(){
  if(!output.isConnected)return;
  const [v,e]=await Promise.all([api(base('/ai-analysis')),api('/ai/engines')]);if(!output.isConnected)return;
  if(!engines||JSON.stringify([engines.local.map(m=>m.id),engines.cloud.map(c=>c.id+c.model),engines.activeModelId,engines.state])!==JSON.stringify([e.local.map(m=>m.id),e.cloud.map(c=>c.id+c.model),e.activeModelId,e.state])){engines=e;fillEngines(e);}
  const c=chosen();
  engineLine.replaceChildren(c.kind==='cloud'&&c.p?h('span',{},pill(c.p.local?tr('Local server'):tr('Cloud'),c.p.local?'ok':'warn'),' ',c.p.local?tr('Runs on a server on your network.'):tr('Selected findings are sent to the provider over the internet.')):
   c.m?h('span',{},pill(tr('Offline'),'ok'),' ',e.activeModelId===c.m.id&&e.state==='ready'?tr('The model is loaded and runs on this server.'):tr('The model will be loaded on this server when the analysis starts. This can take a minute.')):
   h('span',{},pill(tr('No model'),'warn'),' ',tr('No AI model available. Ask an administrator to add one.')));
  running=v.state==='running';submitBtn.disabled=running||!engineSel.value;cancelBtn.hidden=!running;
  if(v.state==='complete')output.replaceChildren(...aiResultView(v,b));
  else if(v.state==='running')output.replaceChildren(h('div',{class:'ai-progress'},h('strong',{},(v.part>1?trf('Part {count}',{count:v.part})+' · ':'')+(v.phase==='merging'?tr('Merging the parts…'):v.phase==='loading'?tr('Loading the selected model…'):v.phase==='writing'?trf('Writing analysis… {count} tokens',{count:v.generatedTokens}):v.phase==='reading'?trf('Reading {count} findings ({tokens} tokens)…',{count:v.findingIds.length,tokens:v.inputTokens}):tr('Preparing evidence…'))),h('progress',{}),h('small',{},v.model?.type==='cloud'?tr('Waiting for the provider to answer.'):tr('Running on the local model. CPU analysis can take a few minutes.'))));
  else output.replaceChildren(h('p',{class:v.state==='failed'?'error-text':'muted'},v.state==='failed'?`${tr('Analysis failed:')} ${tr(v.error)}${v.detail?' ('+v.detail+')':''}`:v.state==='cancelled'?tr('Analysis cancelled.'):tr('Select findings and choose Analyze with AI.')));
 }
 queueMicrotask(()=>perform(aiRefresh));const timer=setInterval(()=>{if(!output.isConnected){clearInterval(timer);return}perform(aiRefresh)},2000);
 return h('section',{class:'panel'},h('h2',{},tr('Analyze with AI')),engineLine,h('p',{class:'help'},tr('Only the selected findings are analyzed. Local models run on this server and nothing leaves it; cloud providers receive the selected findings. AI output is an interpretation that requires review; original evidence and verification statuses are never changed.')),b.findings.length?form:h('p',{},tr('No findings available. Query sources for a target first.')),output);
}
function notesView() {
  return h('div',{},h('section',{class:'panel'},h('div',{class:'section-line'},h('h2',{},tr("Research notes")),btn(tr("+ Add note"),()=>noteDialog())),state.bundle.notes.length?state.bundle.notes.map(n=>h('div',{class:'note'},n.content,h('small',{},date(n.updatedAt),n.findingId?tr(" · Finding: ")+(state.bundle.findings.find(f=>f.id===n.findingId)?.label||''):''))):h('p',{class:'muted'},tr("Keep context, conclusions and next steps here."))),
    h('section',{class:'panel'},h('div',{class:'section-line'},h('h2',{},tr("Relations")),btn(tr("+ Add relation"),relationDialog)),state.bundle.relations.length?state.bundle.relations.map(r=>h('div',{class:'note'},nodeLabel(r.fromId),' → ',r.type,' → ',nodeLabel(r.toId),h('small',{},r.assertionType==='INFERRED'?tr("User inference"):tr("Source stated"),r.note?' · '+r.note:''))):h('p',{class:'muted'},tr("Record links between targets and findings."))));
}
function nodeLabel(id) { return state.bundle.targets.find(t=>t.id===id)?.displayValue || state.bundle.findings.find(f=>f.id===id)?.label || id; }
function noteDialog(findingId) {
  const form=h('form',{class:'form-grid'},field(tr("Note"),h('textarea',{name:'content',required:true,maxlength:20000})),h('button',{type:'submit',class:'primary'},tr("Save note")));
  const d=modal(findingId?tr("Finding note"):tr("Research note"),form);
  formSubmit(form,async data=>{await api(base('/notes'),'POST',{content:data.get('content'),findingId});d.close();await refresh();});
}
function relationDialog() {
  const nodes=Object.fromEntries([...state.bundle.targets.map(t=>[t.id,`${tr("Target")}: ${t.displayValue}`]),...state.bundle.findings.map(f=>[f.id,`${tr("Findings")}: ${f.label} · ${f.value.slice(0,70)}`])]);
  if(Object.keys(nodes).length<2){notice(tr("A relation needs at least two targets or findings."));return;}
  const form=h('form',{class:'form-grid'},field(tr("From"),select('fromId',nodes)),field(tr("Relation type"),h('input',{name:'type',required:true,placeholder:tr("Example: related infrastructure"),maxlength:100})),field(tr("To"),select('toId',nodes)),field(tr("Description"),h('textarea',{name:'note',maxlength:20000})),h('small',{},tr("This relation will be marked as a user inference.")),h('button',{type:'submit',class:'primary'},tr("Save relation")));
  const d=modal(tr("Add relation"),form);formSubmit(form,async data=>{await api(base('/relations'),'POST',Object.fromEntries(data));d.close();await refresh();});
}
async function downloadFile(path, filename, label) {
  const blob=await trackedFetch('/api'+path,{},label,'blob');
  const url=URL.createObjectURL(blob), a=h('a',{href:url,download:filename});
  document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);
}
function deleteInvestigationDialog(inv) {
  modal(tr("Delete this investigation?"),h('div',{},h('strong',{},inv.title),h('p',{},tr("This permanently deletes the investigation and its records.")),h('p',{class:'muted'},tr('Its targets, findings, evidence, notes, AI analysis and report settings are removed.'))),[btn(tr("Delete investigation"),async()=>{
    await api(`/investigations/${inv.id}`,'DELETE');$('#dialog').close();
    if(state.bundle?.investigation.id===inv.id){state.bundle=null;history.replaceState(null,'',location.pathname);}
    await refresh();notice(tr('Investigation deleted.'));},'danger')]);
}
/** "Prepare report": report fields, template report preview and downloads, followed by the analysis and query results it is built from. */
function reportView() {
  const b=state.bundle, holder=h('div',{},h('p',{class:'muted'},tr('Loading…')));
  const label=(map,v)=>tr(map[v]||v);
  const statusNames={draft:'Draft',in_review:'In review',final:'Final'}, confNames={not_determined:'Not determined',low:'Low',medium:'Medium',high:'High'}, decisionNames={draft:'Draft',approved:'Approved',rejected:'Rejected'};
  const options=(list,names)=>Object.fromEntries(list.map(v=>[v,label(names,v)]));
  let reportLang=(()=>{try{return localStorage.getItem('osint-report-language')}catch{return null}})()||language;if(!languages[reportLang])reportLang=language;
  /** Runs the saved analysis again (same findings and engine) so its text matches the report language. */
  async function rerun(ai){
    const cloud=ai.model?.type==='cloud';
    if(cloud&&!confirm(trf('The selected findings will be sent to {name}, an external service, and processed under its terms. Continue only if this is allowed for your investigation.',{name:ai.model.repository})))return;
    await api(base('/ai-analysis'),'POST',{findingIds:ai.findingIds,engine:ai.engine||'',language:reportLang,confirmCloud:cloud});
    notice(tr('Analysis started. The report updates when it finishes.'));
    const t=setInterval(()=>perform(async()=>{const v=await api(base('/ai-analysis'));if(v.state!=='running'){clearInterval(t);if(holder.isConnected)await load();}}),3000);
  }
  async function load(){
    const [r,ai]=await Promise.all([api(base(`/report?lang=${reportLang}`)),api(base('/ai-analysis'))]);if(!holder.isConnected)return;
    const s=r.settings, d=r.data, id=d.report.id;
    const langSel=select('reportLanguage',languages,reportLang);langSel.onchange=()=>{reportLang=langSel.value;try{localStorage.setItem('osint-report-language',reportLang)}catch{};perform(load);};
    const mismatch=ai.state==='complete'&&(ai.language||'en')!==reportLang;
    const form=formSubmit(h('form',{class:'form-grid report-form'},
      field(tr('Research question'),h('textarea',{name:'researchQuestion',maxlength:2000,placeholder:tr('What should this report answer?')},s.researchQuestion)),
      h('div',{class:'report-row'},field(tr('Report status'),select('status',options(r.options.status,statusNames),s.status)),field(tr('Overall confidence'),select('confidence',options(r.options.confidence,confNames),s.confidence)),field(tr('Review decision'),select('decision',options(r.options.decision,decisionNames),s.decision))),
      h('div',{class:'report-row'},field(tr('Reviewer'),h('input',{name:'verifier',maxlength:120,value:s.verifier})),field(tr('Owner of recommended actions'),h('input',{name:'owner',maxlength:120,value:s.owner,placeholder:tr('Analyst')}))),
      h('div',{class:'actions'},h('button',{type:'submit',class:'primary'},tr('Save report details')))),async data=>{await api(base('/report'),'PUT',Object.fromEntries(data));notice(tr('Saved.'));await load();});
    const file=ext=>`${id}-${b.investigation.title.replace(/[^\p{L}\p{N}]+/gu,'-').slice(0,40)}.${ext}`;
    const frame=h('iframe',{class:'report-frame',title:tr('Report preview'),src:`/api${base('/report.html')}?lang=${reportLang}&t=${Date.now()}`});
    holder.replaceChildren(
      h('section',{class:'panel'},h('div',{class:'section-line'},h('div',{},h('h2',{},`${tr('Analysis report')} · ${id}`),h('p',{class:'muted'},trf('{sources} sources · {findings} findings · template version 1.0',{sources:d.metrics.sources,findings:d.metrics.findings}))),
        h('div',{class:'actions'},field(tr('Report language'),langSel),btn(tr('Download PDF'),()=>downloadFile(base(`/report.pdf?lang=${reportLang}`),file('pdf'),tr('Preparing PDF…')),'primary'),btn(tr('Download HTML'),()=>downloadFile(base(`/report.html?lang=${reportLang}&download=1`),file('html'),tr('Download HTML'))),btn(tr('Download report JSON'),()=>downloadFile(base(`/report.json?lang=${reportLang}`),file('json'),tr('Download report JSON'))))),
        mismatch?h('p',{class:'help'},trf('The AI analysis is in {from}; the report is in {to}. Run the analysis again in {to} so the whole report uses one language.',{from:languages[ai.language||'en'],to:languages[reportLang]}),' ',btn(trf('Run AI analysis in {to}',{to:languages[reportLang]}),()=>rerun(ai),'primary')):null,
        ai.state==='running'?h('p',{class:'help'},tr('Analysis running…')):null,
        ai.state!=='complete'?h('p',{class:'help'},tr('No AI analysis yet. The report lists findings without interpretation. Run Analyze with AI on the Analysis tab for AI findings, risks and recommendations.'),' ',btn(tr('Go to Analysis'),()=>{state.tab='analysis';render();})):null,
        form),
      h('section',{class:'panel'},h('h2',{},tr('Preview')),frame),
      h('section',{class:'panel'},h('h2',{},tr('AI analysis')),ai.state==='complete'?h('div',{},aiResultView(ai,b)):h('p',{class:'muted'},tr('Select findings and choose Analyze with AI.'))),
      analysisView(true),
      h('section',{class:'panel'},h('h2',{},tr('Query results')),b.queryRuns.length?h('div',{class:'table-wrap'},h('table',{},h('thead',{},h('tr',{},[tr('Target'),tr('Source'),tr('Status'),tr('Started'),tr('Requests')].map(x=>h('th',{},x)))),
        h('tbody',{},[...b.queryRuns].sort((x,y)=>y.startedAt.localeCompare(x.startedAt)).map(r=>h('tr',{},h('td',{},target(r.targetId)),h('td',{},provider(r.providerId)),h('td',{},pill(statuses[r.status]||r.status,r.status==='SUCCESS'?'ok':'')),h('td',{},date(r.startedAt)),h('td',{},r.requestCount)))))):h('p',{class:'muted'},tr('No queries yet.')),
        h('h3',{},tr('Findings')),b.findings.length?findingTable(b.findings,true):h('p',{class:'muted'},tr('No findings yet.'))),
      h('section',{class:'panel'},h('h2',{},tr('Investigation export')),h('p',{class:'muted'},tr("JSON carries the full investigation. CSV exports findings.")),h('div',{class:'actions'},btn(tr("Download JSON"),()=>downloadReport('json')),btn(tr("Download CSV"),()=>downloadReport('csv')))));
  }
  queueMicrotask(()=>perform(load));
  return holder;
}
function settings() {
  modal(tr('Settings'),h('div',{},h('p',{},tr('All users share this server workspace.')),
    h('p',{},tr('Only administrators can manage users and API keys.')),
    h('p',{},tr('Administrators manage users and API keys in the separate admin panel.')),
    h('h3',{},tr('Choose sources')),state.providers.map(p=>h('div',{class:'note'},h('strong',{},p.name),' ',pill(p.hasKey?tr('Key configured'):p.keyNeed==='REQUIRED'?tr('Key required'):tr('No key required'),p.hasKey?'ok':''),h('small',{},p.host,' · ',link(p.docs,tr('Documentation ↗'))))),
    h('p',{class:'footer-note'},tr('Ping results contain the Windows output. Inspect it for reachability.'))));
}
async function usersDialog() {
  const users=await api('/admin/users');
  const form=h('form',{class:'form-grid'},field(tr('Username'),h('input',{name:'username',required:true,minlength:3,maxlength:40,pattern:'[a-zA-Z0-9_.-]{3,40}',autocomplete:'off'})),
    field(tr('New password'),h('input',{name:'password',type:'password',required:true,minlength:12,maxlength:256,autocomplete:'new-password',placeholder:tr('At least 12 characters')})),
    h('label',{class:'actions'},h('input',{type:'checkbox',name:'admin'}),tr('Administrator')),h('button',{type:'submit',class:'primary'},tr('Add user')));
  modal(tr('Users'),h('div',{},users.map(u=>h('div',{class:'target'},h('div',{},h('strong',{},u.username),pill(u.admin?tr('Administrator'):tr('User')),pill(u.enabled?tr('Enabled'):tr('Disabled'),u.enabled?'ok':'')),h('div',{class:'actions'},btn(tr('Reset password'),()=>resetPasswordDialog(u)),u.username!==state.user.username?btn(u.enabled?tr('Disable'):tr('Enable'),async()=>{await api('/admin/users/'+encodeURIComponent(u.username),'PATCH',{enabled:!u.enabled});await usersDialog();}):null))),h('h3',{},tr('Add user')),form));
  formSubmit(form,async data=>{await api('/admin/users','POST',{username:data.get('username'),password:data.get('password'),admin:data.has('admin')});notice(tr('Saved.'));await usersDialog();});
}
function resetPasswordDialog(user) {
  const form=h('form',{class:'form-grid'},h('strong',{},user.username),field(tr('New password'),h('input',{name:'password',type:'password',required:true,minlength:12,maxlength:256,autocomplete:'new-password'})),h('button',{type:'submit',class:'primary'},tr('Save')));
  modal(tr('Reset password'),form);
  formSubmit(form,async data=>{await api('/admin/users/'+encodeURIComponent(user.username),'PATCH',{password:data.get('password')});if(user.username===state.user.username)login();else if(state.serverAdmin)await openServerManagement();else await usersDialog();notice(tr('Saved.'));});
}
async function keysDialog() {
  const keys=await api('/admin/keys');
  modal(tr('API keys'),h('div',{},h('p',{class:'help'},tr('Keys are encrypted on the server and never returned to the browser. An environment key is used if no stored key exists.')),
    keys.map(key=>h('div',{class:'target'},h('div',{},h('strong',{},key.name),pill(key.configured?tr('Key configured'):tr('Missing key'),key.configured?'ok':'')),h('div',{class:'actions'},btn(key.configured?tr('Replace key'):tr('Add key'),()=>keyDialog(key)),key.stored?btn(tr('Delete stored key'),async()=>{await api('/admin/keys/'+key.id,'DELETE');state.providers=await api('/providers');await keysDialog();}):null)))));
}
function keyDialog(key) {
  const form=h('form',{class:'form-grid'},h('strong',{},key.name),field(tr('API key'),h('input',{name:'key',type:'password',required:true,maxlength:4096,autocomplete:'off'})),h('button',{type:'submit',class:'primary'},tr('Save')));
  modal(tr('API keys'),form);
  formSubmit(form,async data=>{await api('/admin/keys/'+key.id,'PUT',{key:data.get('key')});form.reset();state.providers=await api('/providers');await keysDialog();notice(tr('Saved.'));});
}
function mapView() {
  const points=state.bundle.findings.filter(f=>f.label==='coordinates').map(f=>{
    const values=f.value.split(',');if(values.some(v=>!v.trim()))return null;const parts=values.map(Number);if(parts.length!==2||parts.some(v=>!Number.isFinite(v))||Math.abs(parts[0])>90||Math.abs(parts[1])>180)return null;
    return {f,lat:parts[0],lon:parts[1]};
  }).filter(Boolean);
  if(!points.length)return empty(tr('Map'),tr('No coordinates yet. Query an IP geolocation source first.'));
  const choice=select('mapPoint',Object.fromEntries(points.map((p,i)=>[String(i),`${target(p.f.targetId)} · ${provider(p.f.providerId)} · ${p.lat}, ${p.lon}`])),'0');
  const host=h('div',{class:'map-host'}), details=h('div');
  function stop(){host.querySelectorAll('iframe').forEach(f=>f.src='about:blank');host.replaceChildren();}
  function detail(){stop();const p=points[Number(choice.value)];details.replaceChildren(h('p',{},tr('Coordinates'),`: ${p.lat}, ${p.lon} · `,provider(p.f.providerId),' · ',link(p.f.sourceUrl,tr('View source ↗'))));}
  choice.onchange=detail;detail();
  return h('section',{class:'panel'},h('h2',{},tr('Map')),h('p',{class:'help'},tr('IP locations are approximate network locations, not a person or device location.')),field(tr('Source / Target'),choice),details,
    h('p',{class:'muted'},tr('Loading the map sends these coordinates and your browser IP to OpenStreetMap.')),
    h('div',{class:'actions'},btn(tr('Load map'),()=>{const p=points[Number(choice.value)],params=new URLSearchParams({bbox:[Math.max(-180,p.lon-0.15),Math.max(-90,p.lat-0.1),Math.min(180,p.lon+0.15),Math.min(90,p.lat+0.1)].join(','),layer:'mapnik',marker:`${p.lat},${p.lon}`});host.replaceChildren(h('iframe',{src:'https://www.openstreetmap.org/export/embed.html?'+params,title:tr('Map'),referrerpolicy:'no-referrer',class:'map-frame'}));},'primary'),btn(tr('Stop map'),stop)),host);
}
setInterval(async()=>{
  if(state.loading||state.tab==='map'||!state.bundle||$('#dialog').open||['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName))return;
  if(!state.bundle.queryRuns.some(r=>['PENDING','RUNNING'].includes(r.status)))return;
  state.loading=true;try{await refresh();}catch(e){notice(e.message);}finally{state.loading=false;}
},2500);
perform(async()=>{try{await load();}catch(e){login();if(e.status!==401)notice(e.message);}});
