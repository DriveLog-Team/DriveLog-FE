import 'fake-indexeddb/auto'
import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {Window} from 'happy-dom'
const trip='trip-11111111-1111-4111-8111-111111111111',account='22222222-2222-4222-8222-222222222222',passenger='33333333-3333-4333-8333-333333333333',job='44444444-4444-4444-8444-444444444444',editJob='55555555-5555-4555-8555-555555555555'
async function setup(hash=''){
  const window=new Window({url:'http://127.0.0.1:4173/index.html'+hash,settings:{disableJavaScriptFileLoading:true,disableCSSFileLoading:true,disableJavaScriptEvaluation:true}})
  window.document.write(await readFile(new URL('../index.html',import.meta.url),'utf8'))
  for(const key of ['document','location','history','sessionStorage','localStorage','Option'])globalThis[key]=window[key]
  globalThis.confirm=()=>true
  let tick;globalThis.setInterval=fn=>{tick=fn;return 1}
  const state={phase:'DRIVING',marks:[],receipts:[],uploads:new Set(),saved:false,failMark:true,requests:[],failedJoin:false}
  const data=()=>({tripId:trip,owner:account,phase:state.phase,occupants:[{occupant:account,under14:false},{occupant:passenger,under14:false}],receipts:state.receipts})
  const startedAt=new Date(Date.now()-60000).toISOString()
  globalThis.fetch=async(url,options={})=>{
    const path=new URL(url).pathname,body=options.body&&typeof options.body==='string'?JSON.parse(options.body):options.body
    state.requests.push({path,body,options})
    if(path==='/api/export-profiles')return Response.json([{id:'STORY_15',targetDurationMs:15000,renderingAvailable:true},{id:'SHORT_30',targetDurationMs:30000,renderingAvailable:true}])
    if(path.endsWith('/join')){if(state.failedJoin===false){state.failedJoin=true;throw new TypeError('response lost')}return Response.json({tripId:trip,accountId:passenger,accessToken:'test-passenger'})}
    if(path==='/api/private-trips')return Response.json([data()])
    if(path===`/api/private-trips/${trip}`)return Response.json(data())
    if(path.endsWith('/timeline'))return Response.json({tripId:trip,phase:state.phase,startedAt,entries:state.marks.map(mark=>({mark,authorName:'은서',state:'MARKED'}))})
    if(/\/marks\/[\w-]+$/.test(path)){const id=path.split('/').at(-1);if(!state.marks.some(m=>m.id===id))state.marks.push({id,author:account,...body});if(state.failMark){state.failMark=false;throw new TypeError('response lost')}return Response.json({id,...body})}
    if(path.endsWith('/invites'))return Response.json({url:`https://api.example/join#${trip}.${'a'.repeat(43)}`,expiresAt:new Date(Date.now()+1800000).toISOString()})
    if(path.includes('/consents/')){const occupant=path.split('/').at(-2),role=path.split('/').at(-1);state.receipts=[{occupant,role,actor:account,...body}];return Response.json(data())}
    if(path.endsWith('/context'))return Response.json(data())
    if(path.includes('/media/front')||path.includes('/media/rear')){state.uploads.add(path.split('/').at(-1));if(state.uploads.size===2)state.phase='READY';return Response.json(data())}
    if(path==='/api/media/jobs')return Response.json({jobId:job})
    if(path.endsWith('/edits'))return Response.json({jobId:editJob})
    if(path===`/api/media/jobs/${job}`||path===`/api/media/jobs/${editJob}`)return Response.json({jobId:path.split('/').at(-1),state:'READY',result:{cards:[{id:'moment-001',title:path.endsWith(editJob)?'바꾼 제목':'바다',summary:'함께 웃은 순간',startMs:39000,endMs:46000,cues:[{text:'바다!'}]}]}})
    if(path.endsWith('/finalize')){state.saved=true;state.phase='SAVED';return Response.json(data())}
    if(path==='/api/library')return Response.json(state.saved?[{tripId:trip,title:'바꾼 제목',durationMs:7000,savedAt:new Date().toISOString(),clipUrl:`/api/library/${trip}/clip`}]:[])
    return new Response('',{status:404})
  }
  await import(`../app.js?case=${crypto.randomUUID()}`)
  const button=text=>{const found=[...window.document.querySelectorAll('button')].find(n=>n.textContent===text);assert.ok(found,`button ${text}`);return found}
  const field=label=>{const l=[...window.document.querySelectorAll('label')].find(l=>l.textContent===label);assert.ok(l,`field ${label}`);return l.control||window.document.getElementById(l.htmlFor)}
  const idle=async()=>{for(let i=0;i<200;i++){await new Promise(r=>setTimeout(r,5));if(window.document.querySelector('#content').getAttribute('aria-busy')==='false')return}throw new Error('UI request did not settle')}
  const click=async text=>{button(text).click();await idle()}
  const login=async()=>{field('개인 accountId').value=account;field('개인 accessToken').value='test-owner';button('내 드라이브 연결').closest('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));await idle()}
  return {window,state,button,field,idle,click,login,tick:()=>tick()}
}
test('owner UI completes idempotent mark, invite, consent, uploads, edit and save with selected version',async()=>{
  const ui=await setup();await ui.login();await ui.click('운행 열기')
  await ui.click('지금 순간 남기기');await ui.click('지금 순간 남기기')
  const marks=ui.state.requests.filter(r=>r.path.includes('/marks/'));assert.equal(marks.length,2);assert.equal(marks[0].path,marks[1].path);assert.deepEqual(marks[0].body,marks[1].body);assert.equal(ui.state.marks.length,1)
  await ui.click('한 사람용 초대 링크 발급');assert.ok(ui.window.document.querySelector('.invite-link').textContent.includes(`#${trip}.`))
  ui.state.phase='PENDING';await ui.tick();assert.ok(ui.window.document.body.textContent.includes('동의 대기'))
  const labels=[...ui.window.document.querySelectorAll('label.check')];labels[0].querySelector('input').checked=true;labels[1].querySelector('input').checked=true
  await ui.click('이 내용으로 동의 제출');assert.equal(ui.state.receipts[0].geminiTranscript,true)
  ui.state.phase='UPLOAD';await ui.click('운행 상태 새로고침')
  for(const side of ['전방','후방']){const input=ui.field(`${side} MP4`);Object.defineProperty(input,'files',{value:[new File(['abc'],`${side}.mp4`,{type:'video/mp4'})]});input.dispatchEvent(new ui.window.Event('change'));await ui.click(`${side} 원본 전송`)}
  assert.deepEqual([...ui.state.uploads],['front','rear']);await ui.click('추천 만들기')
  ui.field('제목').value='바꾼 제목';await ui.click('새 버전으로 편집');await ui.click('이 영상 보관')
  const saved=ui.state.requests.findLast(r=>r.path.endsWith('/finalize'));assert.equal(saved.body.jobId,editJob);assert.equal(saved.body.moment,'moment-001');assert.ok(ui.window.document.querySelector('h1').textContent.includes('보관한 순간'))
  assert.ok(!Object.values(ui.window.sessionStorage).some(v=>String(v).includes('test-owner')))
  ui.window.happyDOM.abort()
})
test('invite screen retries identical claim, connects passenger and strips fragment without storing token',async()=>{
  const ui=await setup(`#${trip}.${'a'.repeat(43)}`);ui.field('함께 탈 때 사용할 이름').value='은서'
  const submit=()=>ui.button('초대 수락하고 참여').closest('form').dispatchEvent(new ui.window.Event('submit',{bubbles:true,cancelable:true}))
  submit();await ui.idle();submit();await ui.idle()
  const joins=ui.state.requests.filter(r=>r.path.endsWith('/join'));assert.equal(joins.length,2);assert.equal(joins[0].body.claimKey,joins[1].body.claimKey);assert.equal(joins[0].body.displayName,joins[1].body.displayName)
  assert.equal(ui.window.location.hash,'');assert.ok(ui.window.document.querySelector('h1').textContent.includes('이번 드라이브'))
  assert.equal(ui.window.sessionStorage.length,1);assert.ok(!JSON.stringify(Object.values(ui.window.sessionStorage)).includes('test-passenger'));assert.ok(!ui.window.document.body.innerHTML.includes('test-passenger'))
  ui.window.happyDOM.abort()
})
