
const EMP={razon:"SOLUCIONES INFORMÁTICA TI E.I.R.L.",ruc:"20604792216",dir:"Av. Lima 3959, Urb. Perú - SMP"};
const KEY="facturas_soluciones_v2", CKEY="clientes_soluciones_v2";
function id(){return 'f'+Math.random().toString(36).slice(2,9)}
const seed=[
  {id:id(),doc:"factura",tipo:"venta",serie:"F001",num:"000912",fecha:"2026-10-14",venc:"2026-11-13",cond:"Crédito",docTipo:"RUC",nombre:"Ministerio del Interior",ruc:"20131373237",mon:"PEN",items:[{d:"Laptop Lenovo ThinkCentre Neo 50S",c:3,p:2890}],obs:"OC 2026-4471"},
  {id:id(),doc:"factura",tipo:"venta",serie:"F001",num:"000915",fecha:"2026-10-16",venc:"2026-10-16",cond:"Contado",docTipo:"RUC",nombre:"Pontificia Universidad Católica del Perú",ruc:"20155945860",mon:"PEN",items:[{d:"Monitor HP Serie 5 Pro 23.8\"",c:6,p:640},{d:"Kit Teclado y Mouse HP 125",c:6,p:55}],obs:""},
  {id:id(),doc:"boleta",tipo:"venta",serie:"B001",num:"000204",fecha:"2026-10-17",venc:"2026-10-17",cond:"Contado",docTipo:"DNI",nombre:"Carlos Ramírez Díaz",ruc:"41785236",mon:"PEN",items:[{d:"Mouse Corsair Ironclaw RGB",c:1,p:139}],obs:""},
  {id:id(),doc:"factura",tipo:"compra",serie:"F500",num:"004471",fecha:"2026-10-12",venc:"2026-11-11",cond:"Crédito",docTipo:"RUC",nombre:"Intcomex Perú S.A.C.",ruc:"20100039207",mon:"USD",items:[{d:"Samsung PM9B1 512GB M.2 NVMe",c:20,p:38}],obs:""},
  {id:id(),doc:"factura",tipo:"venta",serie:"F001",num:"000921",fecha:"2026-10-19",venc:"2026-11-18",cond:"Crédito",docTipo:"RUC",nombre:"Municipalidad Distrital de Miraflores",ruc:"20131376503",mon:"PEN",items:[{d:"Case Antryx RX260 MidTower",c:8,p:135}],obs:""}
];
const LS={get(k,f){try{const r=JSON.parse(localStorage.getItem(k));return r??f}catch(e){return f}},set(k,v){try{localStorage.setItem(k,JSON.stringify(v))}catch(e){}}};
let data=LS.get(KEY,null); if(!Array.isArray(data)){data=seed;LS.set(KEY,data);}
let clientes=LS.get(CKEY,null); if(!Array.isArray(clientes)){clientes=data.map(f=>({nombre:f.nombre,ruc:f.ruc,docTipo:f.docTipo}));LS.set(CKEY,clientes);}

const $=s=>document.querySelector(s);
const sym=m=>m==='USD'?'$':'S/';
const sub=f=>f.items.reduce((s,i)=>s+(+i.c||0)*(+i.p||0),0), igv=f=>sub(f)*0.18, tot=f=>sub(f)*1.18;
const fmt=n=>n.toLocaleString('es-PE',{minimumFractionDigits:2,maximumFractionDigits:2});
const docName=d=>d==='boleta'?'Boleta':'Factura';
const today=()=>new Date().toISOString().slice(0,10);
let fTipo='todos', fDoc='todos';

function refreshClientsDL(){
  const seen=new Set(),out=[];
  clientes.forEach(c=>{if(c.nombre&&!seen.has(c.nombre.toLowerCase())){seen.add(c.nombre.toLowerCase());out.push(c)}});
  $('#clientesDL').innerHTML=out.map(c=>`<option value="${(c.nombre||'').replace(/"/g,'&quot;')}">${c.ruc||''}</option>`).join('');
}
function filtered(){
  const q=$('#q').value.trim().toLowerCase(),from=$('#from').value,to=$('#to').value;
  return data.filter(f=>{
    if(fTipo!=='todos'&&f.tipo!==fTipo)return false;
    if(fDoc!=='todos'&&(f.doc||'factura')!==fDoc)return false;
    if(from&&f.fecha<from)return false;
    if(to&&f.fecha>to)return false;
    if(q){const hay=[f.serie,f.num,f.nombre,f.ruc,...f.items.map(i=>i.d)].join(' ').toLowerCase();if(!hay.includes(q))return false;}
    return true;
  }).sort((a,b)=>b.fecha.localeCompare(a.fecha));
}
function render(){
  const list=filtered(),rows=$('#rows');rows.innerHTML='';
  if(!list.length)rows.innerHTML=`<tr><td colspan="7"><div class="empty">No hay comprobantes con esos filtros.</div></td></tr>`;
  const hoy=today();
  list.forEach(f=>{
    const vencido=f.cond==='Crédito'&&f.venc&&f.venc<hoy;
    const tr=document.createElement('tr');
    tr.innerHTML=`<td class="doc"><b>${f.serie}-${f.num}</b><small class="cmp">${docName(f.doc)} · ${f.cond||'Contado'}</small></td>
      <td><span class="tag ${f.tipo==='venta'?'v':'c'}">${f.tipo==='venta'?'Venta':'Compra'}</span></td>
      <td class="who"><b>${f.nombre}</b><small>${f.docTipo||'RUC'} ${f.ruc||'—'}</small></td>
      <td>${f.fecha}</td>
      <td class="venc">${f.venc?`<span class="${vencido?'bad':''}">${f.venc}${vencido?' ⚠':''}</span>`:'—'}</td>
      <td class="money">${sym(f.mon)} ${fmt(tot(f))}</td>
      <td><div class="acts">
        <button class="ico" title="Ver / PDF" data-act="preview" data-id="${f.id}"><svg viewBox="0 0 24 24" fill="none"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/></svg></button>
        <button class="ico" title="XML" data-act="downloadXml" data-id="${f.id}"><svg viewBox="0 0 24 24" fill="none"><path d="M4 4h10l6 6v10H4z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M14 4v6h6" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg></button>
        <button class="ico danger" title="Eliminar" data-act="del" data-id="${f.id}"><svg viewBox="0 0 24 24" fill="none"><path d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
      </div></td>`;
    rows.appendChild(tr);
  });
  $('#foot').textContent=`${list.length} comprobante(s)`;
  const v=list.filter(f=>f.tipo==='venta'&&f.mon==='PEN').reduce((s,f)=>s+tot(f),0);
  const c=list.filter(f=>f.tipo==='compra'&&f.mon==='PEN').reduce((s,f)=>s+tot(f),0);
  $('#stV').textContent='S/ '+fmt(v);$('#stC').textContent='S/ '+fmt(c);$('#stN').textContent=list.length;$('#stL').textContent=list.length?list[0].fecha:'—';
}
$('#seg').onclick=e=>{if(e.target.dataset.t){$('#seg .active').classList.remove('active');e.target.classList.add('active');fTipo=e.target.dataset.t;render();}};
$('#segDoc').onclick=e=>{if(e.target.dataset.d){$('#segDoc .active').classList.remove('active');e.target.classList.add('active');fDoc=e.target.dataset.d;render();}};
['#q','#from','#to'].forEach(s=>$(s).addEventListener('input',render));
$('#clear').onclick=()=>{$('#q').value='';$('#from').value='';$('#to').value='';render();};

let current=null;
function invHtml(f){
  const parte=f.tipo==='venta'?'Cliente':'Proveedor';
  const rows=f.items.map(i=>`<tr><td>${i.d}</td><td class="r">${i.c}</td><td class="r">${sym(f.mon)} ${fmt(+i.p)}</td><td class="r">${sym(f.mon)} ${fmt(i.c*i.p)}</td></tr>`).join('');
  return `<div class="inv-h"><div class="inv-co"><b>${EMP.razon}</b><span>RUC ${EMP.ruc}</span><span>${EMP.dir}</span></div>
    <div class="inv-doc"><div class="l">${docName(f.doc)} ${f.tipo==='venta'?'de venta':'de compra'}</div><div class="n">${f.serie}-${f.num}</div><div class="ruc">RUC ${EMP.ruc}</div></div></div>
   <div class="inv-m"><div><b>${parte}:</b> ${f.nombre}</div><div><b>${f.docTipo||'RUC'}:</b> ${f.ruc||'—'}</div>
     <div><b>Emisión:</b> ${f.fecha}</div><div><b>Vencimiento:</b> ${f.venc||'—'}</div>
     <div><b>Condición:</b> ${f.cond||'Contado'}</div><div><b>Moneda:</b> ${f.mon==='USD'?'Dólares (USD)':'Soles (PEN)'}</div></div>
   <div class="inv-wrap"><table><thead><tr><th>Descripción</th><th class="r">Cant.</th><th class="r">P. unit.</th><th class="r">Importe</th></tr></thead><tbody>${rows}</tbody></table></div>
   <div class="inv-foot">
     <div class="inv-obs">${f.obs?`<b>Observaciones</b>${f.obs}`:''}</div>
     <div class="inv-t"><div><span>Op. gravada</span><b>${sym(f.mon)} ${fmt(sub(f))}</b></div><div><span>IGV 18%</span><b>${sym(f.mon)} ${fmt(igv(f))}</b></div><div class="g"><span>Total</span><span>${sym(f.mon)} ${fmt(tot(f))}</span></div></div>
   </div>`;
}
window.preview=fid=>{current=data.find(x=>x.id===fid);$('#invView').innerHTML=invHtml(current);$('#prevM').classList.add('open');document.body.style.overflow='hidden';};
function closePrev(){$('#prevM').classList.remove('open');document.body.style.overflow='auto';}
window.closePrev=closePrev;
$('#prevPdf').onclick=()=>window.print();
$('#prevXml').onclick=()=>current&&downloadXml(current.id);

function xe(s){return String(s).replace(/[<>&'"]/g,c=>({'<':'&lt;','>':'&gt;','&':'&amp;',"'":'&apos;','"':'&quot;'}[c]))}
function invoiceXml(f){
  const tc=f.doc==='boleta'?'03':(f.tipo==='venta'?'01':'00');
  const lines=f.items.map((i,n)=>`    <cac:InvoiceLine><cbc:ID>${n+1}</cbc:ID><cbc:InvoicedQuantity unitCode="NIU">${i.c}</cbc:InvoicedQuantity><cbc:LineExtensionAmount currencyID="${f.mon}">${(i.c*i.p).toFixed(2)}</cbc:LineExtensionAmount><cac:Item><cbc:Description>${xe(i.d)}</cbc:Description></cac:Item><cac:Price><cbc:PriceAmount currencyID="${f.mon}">${(+i.p).toFixed(2)}</cbc:PriceAmount></cac:Price></cac:InvoiceLine>`).join('\n');
  const rol=f.tipo==='venta'?'AccountingCustomerParty':'AccountingSupplierParty';
  const docSchema=(f.docTipo==='DNI')?'1':'6';
  return `<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>
  <cbc:ID>${f.serie}-${f.num}</cbc:ID>
  <cbc:IssueDate>${f.fecha}</cbc:IssueDate>
  <cbc:DueDate>${f.venc||f.fecha}</cbc:DueDate>
  <cbc:InvoiceTypeCode>${tc}</cbc:InvoiceTypeCode>
  <cbc:DocumentCurrencyCode>${f.mon}</cbc:DocumentCurrencyCode>
  <cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>${f.cond==='Crédito'?'Credito':'Contado'}</cbc:PaymentMeansID></cac:PaymentTerms>
  <cac:AccountingSupplierParty><cac:Party><cac:PartyLegalEntity><cbc:RegistrationName>${xe(EMP.razon)}</cbc:RegistrationName><cbc:CompanyID schemeID="6">${EMP.ruc}</cbc:CompanyID></cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>
  <cac:${rol}><cac:Party><cac:PartyLegalEntity><cbc:RegistrationName>${xe(f.nombre)}</cbc:RegistrationName><cbc:CompanyID schemeID="${docSchema}">${xe(f.ruc||'')}</cbc:CompanyID></cac:PartyLegalEntity></cac:Party></cac:${rol}>
  <cac:TaxTotal><cbc:TaxAmount currencyID="${f.mon}">${igv(f).toFixed(2)}</cbc:TaxAmount></cac:TaxTotal>
  <cac:LegalMonetaryTotal><cbc:LineExtensionAmount currencyID="${f.mon}">${sub(f).toFixed(2)}</cbc:LineExtensionAmount><cbc:PayableAmount currencyID="${f.mon}">${tot(f).toFixed(2)}</cbc:PayableAmount></cac:LegalMonetaryTotal>
${lines}
</Invoice>`;
}
function dl(name,text,type){const b=new Blob([text],{type});const u=URL.createObjectURL(b);const a=document.createElement('a');a.href=u;a.download=name;a.click();URL.revokeObjectURL(u);}
window.downloadXml=fid=>{const f=data.find(x=>x.id===fid);dl(`${f.serie}-${f.num}.xml`,invoiceXml(f),'application/xml');};
$('#expAll').onclick=()=>{const list=filtered();if(!list.length)return;const body=list.map(invoiceXml).map(x=>x.replace(/^<\?xml[^>]*>\s*/,'')).join('\n');dl(`comprobantes.xml`,`<?xml version="1.0" encoding="UTF-8"?>\n<Comprobantes fecha="${today()}">\n${body}\n</Comprobantes>`,'application/xml');};

const itemRows=$('#itemRows');
function addRow(d='',c='',p=''){
  const r=document.createElement('div');r.className='ir';
  r.innerHTML=`<input class="d" placeholder="Producto / servicio" value="${d}"><input class="c" type="number" min="0" placeholder="0" value="${c}"><input class="p" type="number" min="0" step="0.01" placeholder="0.00" value="${p}"><input class="imp" readonly tabindex="-1" style="background:#f7f9fb"><button class="rm" title="Quitar">×</button>`;
  itemRows.appendChild(r);
  r.querySelector('.rm').onclick=()=>{r.remove();recalc();};
  r.querySelectorAll('.c,.p').forEach(i=>i.addEventListener('input',recalc));recalc();
}
function recalc(){let s=0;itemRows.querySelectorAll('.ir').forEach(r=>{const c=+r.querySelector('.c').value||0,p=+r.querySelector('.p').value||0;r.querySelector('.imp').value=fmt(c*p);s+=c*p;});$('#fSub').textContent=fmt(s);$('#fIgv').textContent=fmt(s*0.18);$('#fTot').textContent=fmt(s*1.18);}
$('#addItem').onclick=()=>addRow();
function syncToggle(wrap,val){document.querySelectorAll('#'+wrap+' label').forEach(l=>l.classList.toggle('sel',l.dataset.v===val));}
$('#tgDoc').addEventListener('change',e=>{syncToggle('tgDoc',e.target.value);const s=$('#fSerie');if(!s.value||/^[FB]0*1?$/i.test(s.value))s.value=e.target.value==='boleta'?'B001':'F001';});
$('#tgTipo').addEventListener('change',e=>{syncToggle('tgTipo',e.target.value);$('#lblParte').textContent=e.target.value==='venta'?'Cliente':'Proveedor';});
$('#fDocTipo').addEventListener('change',e=>{$('#fRuc').maxLength=e.target.value==='DNI'?8:11;$('#fRuc').placeholder=e.target.value==='DNI'?'DNI (8 dígitos)':'20xxxxxxxxx';});
$('#fNombre').addEventListener('input',e=>{const c=clientes.find(x=>(x.nombre||'').toLowerCase()===e.target.value.trim().toLowerCase());if(c){if(c.ruc&&!$('#fRuc').value)$('#fRuc').value=c.ruc;if(c.docTipo)$('#fDocTipo').value=c.docTipo;}});
$('#newBtn').onclick=()=>{
  ['fNombre','fRuc','fNum','fObs'].forEach(i=>$('#'+i).value='');
  document.querySelector('#tgDoc input[value="factura"]').checked=true;syncToggle('tgDoc','factura');
  document.querySelector('#tgTipo input[value="venta"]').checked=true;syncToggle('tgTipo','venta');
  $('#lblParte').textContent='Cliente';$('#fMon').value='PEN';$('#fDocTipo').value='RUC';$('#fCond').value='Contado';
  $('#fSerie').value='F001';$('#fFecha').value=today();$('#fVenc').value=today();
  refreshClientsDL();itemRows.innerHTML='';addRow();
  $('#formM').classList.add('open');document.body.style.overflow='hidden';
};
function closeForm(){$('#formM').classList.remove('open');document.body.style.overflow='auto';}
window.closeForm=closeForm;
$('#saveBtn').onclick=()=>{
  const items=[...itemRows.querySelectorAll('.ir')].map(r=>({d:r.querySelector('.d').value.trim(),c:+r.querySelector('.c').value||0,p:+r.querySelector('.p').value||0})).filter(i=>i.d&&i.c>0);
  const nombre=$('#fNombre').value.trim(), ruc=$('#fRuc').value.trim(), docTipo=$('#fDocTipo').value;
  if(!$('#fSerie').value.trim()||!$('#fNum').value.trim())return alert('Completa serie y número.');
  if(!nombre)return alert('Indica el cliente o proveedor.');
  if(!items.length)return alert('Agrega al menos una línea con descripción y cantidad.');
  const rec={id:id(),doc:document.querySelector('#tgDoc input:checked').value,tipo:document.querySelector('#tgTipo input:checked').value,
    serie:$('#fSerie').value.trim().toUpperCase(),num:$('#fNum').value.trim(),fecha:$('#fFecha').value,venc:$('#fVenc').value,
    cond:$('#fCond').value,docTipo,nombre,ruc,mon:$('#fMon').value,items,obs:$('#fObs').value.trim()};
  data.push(rec);LS.set(KEY,data);
  const ex=clientes.find(c=>(c.nombre||'').toLowerCase()===nombre.toLowerCase());
  if(!ex){clientes.push({nombre,ruc,docTipo});LS.set(CKEY,clientes);}
  else if(ruc&&!ex.ruc){ex.ruc=ruc;ex.docTipo=docTipo;LS.set(CKEY,clientes);}
  closeForm();render();
};
window.del=fid=>{if(confirm('¿Eliminar este comprobante?')){data=data.filter(x=>x.id!==fid);LS.set(KEY,data);render();}};
[formM,prevM].forEach(m=>m.addEventListener('click',e=>{if(e.target===m){m.classList.remove('open');document.body.style.overflow='auto';}}));
refreshClientsDL();render();

// Acciones de botones (sin manejadores en línea, compatible con la CSP del sitio).
document.addEventListener('click',function(e){
  var b=e.target.closest('[data-act]'); if(!b) return;
  var a=b.dataset.act;
  if(a==='closeForm') closeForm(); else if(a==='closePrev') closePrev();
  else if(a==='preview') preview(b.dataset.id); else if(a==='downloadXml') downloadXml(b.dataset.id);
  else if(a==='del') del(b.dataset.id);
});
document.querySelectorAll('img[data-ocultar-si-falla]').forEach(function(i){
  var ocultar=function(){i.style.display='none';};
  if(i.complete&&i.naturalWidth===0) ocultar(); i.addEventListener('error',ocultar);
});
