import http from 'node:http'
import {readFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {resolve,extname,sep} from 'node:path'
const root=fileURLToPath(new URL('.',import.meta.url)),fixture=process.argv.includes('--fixture'),port=Number(process.env.PORT||4173)
const trip='trip-11111111-1111-4111-8111-111111111111',account='22222222-2222-4222-8222-222222222222',passenger='33333333-3333-4333-8333-333333333333',job='44444444-4444-4444-8444-444444444444'
const state={phase:'DRIVING',owner:account,marks:[],receipts:[],uploads:new Set(),saved:false,job:false,edits:0}
const tripData=()=>({tripId:trip,owner:state.owner,phase:state.phase,occupants:[{occupant:account,under14:false},{occupant:passenger,under14:false}],receipts:state.receipts})
function json(res,data,status=200){res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(data))}
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'}
http.createServer(async(req,res)=>{
  try{
    const url=new URL(req.url,`http://127.0.0.1:${port}`),path=url.pathname
    if(path.startsWith('/api/')||path.startsWith('/__fixture')){
      if(!fixture){json(res,{error:'No local API. Enter the HTTPS API origin or host these files on the API origin.'},404);return}
      let body='';for await(const chunk of req)body+=chunk
      const data=body&&req.headers['content-type']?.includes('json')?JSON.parse(body):{}
      if(path==='/__fixture/park'){state.phase='PENDING';json(res,tripData());return}
      if(path==='/__fixture/state'){json(res,{...state,uploads:[...state.uploads]});return}
      if(path==='/api/status'){json(res,{service:'drivelog-fixture',status:'ready'});return}
      if(path==='/api/export-profiles'){json(res,[{id:'STORY_15',targetDurationMs:15000,renderingAvailable:true},{id:'SHORT_30',targetDurationMs:30000,renderingAvailable:true}]);return}
      if(path.endsWith('/join')){json(res,{tripId:trip,accountId:passenger,accessToken:'fixture-passenger'});return}
      if(!req.headers.authorization){json(res,{},401);return}
      const actor=req.headers.authorization.includes('passenger')?passenger:account
      if(path==='/api/private-trips'){json(res,[tripData()]);return}
      if(path==='/api/library'){json(res,state.saved?[{tripId:trip,title:'처음 만난 바다',durationMs:15000,savedAt:new Date().toISOString(),clipUrl:`/api/library/${trip}/clip`}]:[]);return}
      if(path.endsWith('/timeline')){json(res,{tripId:trip,phase:state.phase,startedAt:new Date(Date.now()-120000).toISOString(),entries:state.marks.map(mark=>({mark,authorName:'함께 탄 친구',state:state.job?'CLIP_READY':'MARKED',...(state.job?{title:'함께 웃은 순간',clipUrl:`/api/private-trips/${trip}/marks/${mark.id}/clip`}:{})}))});return}
      if(/\/marks\/[\w-]+$/.test(path)&&req.method==='PUT'){let mark=state.marks.find(m=>m.id===path.split('/').at(-1));if(!mark){mark={id:path.split('/').at(-1),author:actor,...data};state.marks.push(mark)}json(res,mark);return}
      if(path.endsWith('/display-name')){json(res,data);return}
      if(path.includes('/consents/')){const parts=path.split('/'),occupant=parts.at(-2),role=parts.at(-1);state.receipts=state.receipts.filter(r=>!(r.occupant===occupant&&r.role===role));state.receipts.push({occupant,role,actor,...data});if(state.receipts.length>=2&&state.receipts.every(r=>r.extraction&&r.geminiTranscript))state.phase='UPLOAD';json(res,tripData());return}
      if(path.endsWith('/context')){json(res,tripData());return}
      if(path.includes('/media/front')||path.includes('/media/rear')){state.uploads.add(path.split('/').at(-1));if(state.uploads.size===2)state.phase='READY';json(res,tripData());return}
      if(path==='/api/media/jobs'&&req.method==='POST'){state.job=true;json(res,{jobId:job},202);return}
      if(path.endsWith('/edits')){state.edits++;json(res,{jobId:job},202);return}
      if(path===`/api/media/jobs/${job}`){json(res,{jobId:job,state:'READY',result:{status:'READY',cards:[{id:'moment-001',title:'처음 만난 바다',summary:'터널을 지나 바다가 보이던 순간의 대화예요.',startMs:39000,endMs:54000,cues:[{startMs:40000,endMs:42000,text:'바다 보인다!'}]}]}});return}
      if(path.endsWith('/finalize')){state.phase='SAVED';state.saved=true;json(res,tripData());return}
      if(path.endsWith('/invites')){json(res,{url:`http://127.0.0.1:${port}/index.html#${trip}.${'a'.repeat(43)}`,expiresAt:new Date(Date.now()+1800000).toISOString()});return}
      if(path.includes('/picks/')){json(res,{markId:path.split('/').at(-1)});return}
      if(path===`/api/private-trips/${trip}`){if(req.method==='DELETE')state.phase='DISCARDED';json(res,tripData());return}
      json(res,{error:'Fixture endpoint not configured'},404);return
    }
    const file=resolve(root,'.'+decodeURIComponent(path==='/'?'/index.html':path))
    if(!file.startsWith(root.endsWith(sep)?root:root+sep)||!mime[extname(file)]){res.writeHead(404);res.end();return}
    let source=await readFile(file)
    if(fixture&&extname(file)==='.html')source=Buffer.from(source.toString().replace('<main id="content"','<p class="notice">프론트 검증용 합성 데이터 · 실제 서버·계정 아님</p><main id="content"'))
    res.writeHead(200,{'Content-Type':mime[extname(file)],'Cache-Control':'no-store'});res.end(source)
  }catch{res.writeHead(400);res.end('Cannot serve request')}
}).listen(port,'127.0.0.1',()=>console.log(`DriveLog ${fixture?'fixture':'static'} server http://127.0.0.1:${port}`))
