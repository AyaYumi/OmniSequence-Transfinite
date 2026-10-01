const fs=require('fs'),path=require('path'),http=require('http');
const {chromium}=require('C:/Users/ATIR/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const base=__dirname,out=path.dirname(base);
(async()=>{
 const server=http.createServer((req,res)=>{
  const p=path.join(base,new URL(req.url,'http://localhost').pathname);
  fs.readFile(p,(e,b)=>{if(e){res.statusCode=404;res.end('missing');return;}
   res.setHeader('Content-Type',p.endsWith('.js')?'text/javascript':'text/html');res.end(b);});
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true,args:['--disable-gpu-sandbox','--enable-unsafe-swiftshader','--disable-dev-shm-usage']});
 try{
  const page=await browser.newPage({viewport:{width:1600,height:1800}});
  page.on('pageerror',e=>console.error(e));
  await page.goto(`http://127.0.0.1:${server.address().port}/scene.html`);
  await page.waitForFunction(()=>window.rendererReady,{timeout:60000});
  for(let v=0;v<4;v++)for(const detail of [false,true]){
   const info=await page.evaluate(({v,detail})=>window.build(v,detail),{v,detail});
   const filename=`${'ABCD'[v]}-${detail?'detail':'overview'}`;
   for(const glow of [false,true]){
    const data=await page.evaluate(glow=>window.capture(glow),glow);
    fs.writeFileSync(path.join(out,filename+(glow?'-glow':'-base')+'.png'),Buffer.from(data.split(',')[1],'base64'));
   }
   console.log(JSON.stringify({filename,...info}));
  }
 }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exit(1)});
