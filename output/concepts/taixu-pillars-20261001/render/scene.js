import * as T from './three.module.js';

// Offline concept geometry. No game assets or runtime behavior are modified.
const W=1600,H=1800;
const renderer=new T.WebGLRenderer({antialias:true,preserveDrawingBuffer:true});
renderer.setSize(W,H); renderer.setPixelRatio(1);
renderer.shadowMap.enabled=true; renderer.shadowMap.type=T.PCFSoftShadowMap;
renderer.outputColorSpace=T.SRGBColorSpace; renderer.toneMapping=T.ACESFilmicToneMapping;
renderer.toneMappingExposure=1.10; document.body.appendChild(renderer.domElement);
const scene=new T.Scene(); scene.background=new T.Color('#0c1929');
const camera=new T.OrthographicCamera(-80*W/H,80*W/H,80,-80,.1,800);
camera.position.set(172,147,230); camera.lookAt(0,64,0);
scene.add(new T.HemisphereLight(0xc2eaff,0x36445b,1.35));
const key=new T.DirectionalLight(0xffefdb,3.1); key.position.set(-80,165,120);
key.castShadow=true; key.shadow.mapSize.set(4096,4096);
Object.assign(key.shadow.camera,{left:-95,right:95,top:105,bottom:-105,near:1,far:450});
key.shadow.bias=-.00035; key.shadow.normalBias=.09;
key.target.position.set(0,63,0);scene.add(key,key.target);
const rim=new T.DirectionalLight(0x76cce6,2.5);rim.position.set(100,90,-110);scene.add(rim);
const fill=new T.DirectionalLight(0xc7dafa,.8);fill.position.set(100,90,140);scene.add(fill);

function mat(color,metal=.1,rough=.4,emissive=null){
 const m=new T.MeshStandardMaterial({color,metalness:metal,roughness:rough});
 if(emissive){m.emissive=new T.Color(emissive);m.emissiveIntensity=.8;m.userData.glow=emissive;}
 return m;
}
const M={
 jade:mat('#e5e7dc',.1,.4), pale:mat('#f7f4dd',.08,.46),
 shadow:mat('#657b87',.24,.44), dark:mat('#173642',.28,.4),
 gold:mat('#c59a4f',.55,.33), goldLight:mat('#e3ba6c',.4,.35),
 cyan:mat('#7ad5db',.15,.28,'#56c4d9'), ice:mat('#cef7f1',.12,.28,'#89dcdf'),
 warm:mat('#f5daa0',.15,.35,'#d7b16b'), roof:mat('#347481',.35,.44),
 glass:mat('#397681',.35,.22), inset:mat('#304e5c',.22,.42),
};
const boxGeo=new T.BoxGeometry(1,1,1);const cache=new Map();
function mesh(g,geo,m){const o=new T.Mesh(geo,m);o.castShadow=true;o.receiveShadow=true;g.add(o);return o;}
function box(g,x,y,z,w,h,d,m=M.jade,ry=0){const o=mesh(g,boxGeo,m);o.position.set(x,y,z);o.scale.set(w,h,d);o.rotation.y=ry;return o;}
function cylinder(g,x,y,z,rt,rb,h,m=M.jade,n=8,rot=Math.PI/8){const k=[rt,rb,h,n].join();if(!cache.has(k))cache.set(k,new T.CylinderGeometry(rt,rb,h,n));const o=mesh(g,cache.get(k),m);o.position.set(x,y,z);o.rotation.y=rot;return o;}
function ring(g,y,r,width,h,m=M.gold,n=96,start=0,end=Math.PI*2){
 const vertices=[],indices=[];const ri=r-width/2,ro=r+width/2;
 for(let j=0;j<=n;j++) {const a=start+(end-start)*j/n;for(const [rad,dy] of [[ri,-h/2],[ro,-h/2],[ri,h/2],[ro,h/2]])vertices.push(rad*Math.cos(a),y+dy,rad*Math.sin(a));}
 for(let j=0;j<n;j++){const k=j*4,l=k+4;for(const q of [[k,l,k+1,l+1],[k+2,k+3,l+2,l+3],[k,k+2,l,l+2],[k+1,l+1,k+3,l+3]])indices.push(q[0],q[1],q[2],q[2],q[1],q[3]);}
 const geo=new T.BufferGeometry();geo.setAttribute('position',new T.Float32BufferAttribute(vertices,3));geo.setIndex(indices);geo.computeVertexNormals();
 const o=mesh(g,geo,m);o.material.side=T.DoubleSide;return o;
}
function hoop(g,center,r,thickness,m,angles=[0,0,0],segments=96){
 const geo=new T.TorusGeometry(r,thickness,6,segments);const o=mesh(g,geo,m);
 o.rotation.set(Math.PI/2+angles[0],angles[1],angles[2]);o.position.set(...center);return o;
}
function bar(g,a,b,w,d,m){const mid=new T.Vector3(...a).add(new T.Vector3(...b)).multiplyScalar(.5);const dir=new T.Vector3(...b).sub(new T.Vector3(...a));const o=mesh(g,boxGeo,m);o.position.copy(mid);o.scale.set(w,dir.length(),d);o.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),dir.normalize());return o;}
function crystal(g,x,y,z,r,h,m=M.ice,n=6){const group=new T.Group();group.position.set(x,y,z);g.add(group);cylinder(group,0,h*.14,0,0,r,h*.64,m,n,0);cylinder(group,0,-h*.36,0,r,0,h*.36,m,n,0);return group;}
function local(g,x,y,z,ry=0){const q=new T.Group();q.position.set(x,y,z);q.rotation.y=ry;g.add(q);return q;}
function radialBoxes(g,count,r,y,w,h,d,m,offset=0){for(let i=0;i<count;i++){const a=i*Math.PI*2/count+offset;box(g,Math.sin(a)*r,y,Math.cos(a)*r,w,h,d,m,a);}}
function cornerPosts(g,y0,y1,r,width,m=M.jade){for(const x of [-r,r])for(const z of [-r,r])box(g,x,(y0+y1)/2,z,width,y1-y0,width,m);}
function trimSquare(g,y,size,h=.3,m=M.gold){for(const s of [-1,1]) {box(g,s*size/2,y,0,.28,h,size,m);box(g,0,y,s*size/2,size,h,.28,m);}}
function fins(g,y,r,size=1,matl=M.jade){for(let i=0;i<4;i++){
 const q=local(g,0,0,0,i*Math.PI/2);bar(q,[r,y-5*size,0],[r+2.4*size,y+1*size,0],1.0*size,1.3*size,matl);
 bar(q,[r+2.4*size,y+1*size,0],[r+1.7*size,y+5*size,0],.8*size,1.1*size,matl);
 bar(q,[r+2.6*size,y+.7*size,0],[r+1.9*size,y+5.6*size,0],.18*size,.3*size,M.gold);
}}
function runes(g,y,r,count=16){for(let i=0;i<count;i++){const a=i*2*Math.PI/count;const q=local(g,Math.sin(a)*r,y,Math.cos(a)*r,a);box(q,0,0,0,.16,.7,.08,M.warm);box(q,.25,.17,0,.26,.12,.08,M.goldLight);}}
function baseRing(g,r,y){
 ring(g,y-1.0,r-1.1,2.7,1.2,M.jade);ring(g,y-.6,r+.4,.34,1.2,M.gold);
 ring(g,y+.1,r-1,1.4,.3,M.glass);ring(g,y+.28,r-2.2,.23,.26,M.cyan);ring(g,y+.2,r+.35,.16,.3,M.warm);
 for(let i=0;i<64;i++){const a=i*Math.PI*2/64;box(g,(r-1)*Math.sin(a),y+.44,(r-1)*Math.cos(a),.27,.26,2.6,i%4===0?M.gold:M.pale,a);}
 for(let i=0;i<24;i++){const a=i*Math.PI*2/24;const q=local(g,r*Math.sin(a),y-1.6,r*Math.cos(a),a);box(q,0,0,0,2.2,.85,.65,M.shadow);box(q,0,0,.36,.7,.46,.1,M.gold);}
}
function common(g){
 baseRing(g,30,36);baseRing(g,48,64);baseRing(g,32,96);
 for(let i=0;i<8;i++){const a=i*Math.PI/4,q=local(g,0,0,0,a);
 box(q,0,63.4,23,2.25,1.0,31,M.jade);box(q,0,64.02,23,.42,.16,31,M.cyan);
 for(const s of [-1,1])box(q,s*.98,64.1,23,.16,.2,31,M.gold);
 }
 ring(g,63,6.6,4.7,1.8,M.jade,48);ring(g,64.1,7.7,.3,.35,M.gold);
 for(let i=-2;i<=2;i++){box(g,i*1.25,65,-6.4,1.04,1.1,.85,M.dark);box(g,i*1.25,65.05,-6.86,.78,.55,.07,i===0?M.ice:M.cyan);}
 cylinder(g,0,6,0,4.0,.45,12,M.jade,8);cylinder(g,0,11,0,4.5,4.0,1.3,M.gold,8);crystal(g,0,1,0,.8,6,M.cyan);
}

function outerA(g,top){
 const b=25,foot=46;
 cylinder(g,0,foot,0,4.25,2.5,4,M.jade,8);cylinder(g,0,foot+2.2,0,4.45,4.45,.5,M.gold,8);
 cylinder(g,0,35,0,1.25,.6,18,M.shadow,8);cornerPosts(g,28,46,.85,.36,M.jade);
 crystal(g,0,25,0,.85,6,M.cyan);hoop(g,[0,26,0],2,.08,M.cyan);
 const crown=top-8;cornerPosts(g,47,crown,1.8,.65,M.gold);
 cylinder(g,0,(48+crown)/2,0,1.3,1.3,crown-48,M.dark,8);
 for(let y=48;y<crown-3;y+=9){
  const seg=Math.min(7,crown-y-.8);const taper=y>crown-15?.88:1;
  for(let i=0;i<4;i++){const q=local(g,0,0,0,i*Math.PI/2);
   box(q,0,y+seg/2,2.1,2.7*taper,seg,.68,M.jade);box(q,0,y+seg/2,2.47,.48,seg-1,.1,M.inset);
   box(q,0,y+seg/2,2.55,.14,seg-1.5,.08,M.cyan);
   for(const s of [-1,1])box(q,s*1.1*taper,y+seg/2,2.48,.12,seg,.1,M.gold);
  }
  cylinder(g,0,y-.4,0,2.95,2.95,.6,M.gold,8);cylinder(g,0,y-.9,0,2.7,2.7,.45,M.shadow,8);
 }
 fins(g,crown-4,2.3,.73);cylinder(g,0,crown+1,0,4.2,2.7,2.0,M.jade,8);
 cylinder(g,0,crown+2.25,0,4.45,4.45,.55,M.gold,8);ring(g,crown+3.15,3.1,.8,.5,M.pale,32);
 crystal(g,0,crown+6,0,1.45,7,M.cyan);radialBoxes(g,4,3.1,crown+5,.4,4,.4,M.gold,Math.PI/4);
 hoop(g,[0,crown+4,0],4.15,.10,M.warm);runes(g,crown+1,4.23,8);
}
function centralA(g){
 for(const y of [17,36,53,80,98,118]){
  ring(g,y,6.1,1.3,1.4,M.jade,48);ring(g,y+.82,6.4,.4,.3,M.gold,48);
  ring(g,y-.8,5.9,.18,.16,M.cyan,48);radialBoxes(g,8,6.2,y,1.1,2.2,.65,M.gold,Math.PI/8);
 }
 for(let i=0;i<4;i++){const q=local(g,0,0,0,i*Math.PI/2+Math.PI/4);
  for(const [lo,hi,r] of [[14,48,4.4],[49,82,5.1],[84,116,4.1]]){
   bar(q,[r,lo,0],[r+.7,hi-3,0],1.1,1.4,M.jade);box(q,r+.6,(lo+hi)/2,0,.25,hi-lo,.24,M.gold);
   box(q,r+.3,(lo+hi)/2,.8,.2,hi-lo-.8,.1,M.cyan);
  }
 }
 cylinder(g,0,120,0,8.1,6.1,2,M.jade,8);cylinder(g,0,121.3,0,8.35,8.35,.55,M.gold,8);
 for(let i=0;i<8;i++){const a=i*Math.PI/4;crystal(g,5.5*Math.sin(a),124.0,5.5*Math.cos(a),.8,6,M.pale,4);}
 crystal(g,0,129,0,2.4,13,M.ice);hoop(g,[0,122.5,0],7.5,.12,M.warm);
 crystal(g,0,72,0,3.2,22,M.cyan,6);crystal(g,0,49,0,1.45,11,M.ice,6);
 for(const [r,angles] of [[9,[.55,0,.55]],[10,[-.5,.4,-.5]],[7.8,[.2,.6,1.2]]])hoop(g,[0,72,0],r,.16,M.goldLight,angles);
 cylinder(g,0,73,0,.18,.18,102,M.ice,8);
 fins(g,94,4.6,1.3);fins(g,28,4.0,.8);
}

function outerB(g,top){
 cylinder(g,0,45,0,3.7,2.6,3,M.jade,8);ring(g,47,3.8,.65,.6,M.gold,32);
 for(const [y,h,r] of [[35,12,1.8],[54,13,2.3],[54+(top-62)/2,13,2.0],[top-7,13,2.0]]){
  crystal(g,0,y,0,r,h,M.cyan,6);
  for(let i=0;i<4;i++) {const q=local(g,0,0,0,i*Math.PI/2+Math.PI/4);
   box(q,2.6,y,0,.7,h-2,1.0,M.jade);box(q,3.0,y,0,.14,h-1,.36,M.gold);
  }
  ring(g,y-h*.34,3.0,.72,.7,M.jade,32);ring(g,y+h*.30,2.9,.45,.4,M.gold,32);
 }
 for(const y of [63.7,84]){ring(g,y,3.9,.35,.5,M.gold,32);hoop(g,[0,y+.4,0],4.4,.10,M.cyan);}
 fins(g,top-6,3.0,1.02);ring(g,top-2,4.0,.52,.5,M.jade,32);
 crystal(g,0,top+3,0,1.55,8,M.ice);hoop(g,[0,top-.5,0],3.0,.13,M.gold,[.2,0,.35]);
 crystal(g,0,23,0,.85,6,M.ice);cylinder(g,0,(top+25)/2,0,.13,.13,top-25,M.cyan,8);
}
function centralB(g){
 for(const [y,r] of [[18,5.1],[38,7.2],[55,8.7],[91,10],[111,7.4],[121,5.2]]){
  for(let i=0;i<4;i++){const a=i*Math.PI/2+.16;ring(g,y,r,1.5,1.5,M.jade,18,a,a+Math.PI/2-.32);ring(g,y+.9,r+.12,.32,.35,M.gold,18,a,a+Math.PI/2-.32);}
  hoop(g,[0,y-.6,0],r-.2,.10,M.cyan);radialBoxes(g,4,r,y,1,2.4,1.9,M.gold,Math.PI/4);
 }
 for(let i=0;i<4;i++){
  const q=local(g,0,0,0,i*Math.PI/2+.32);
  for(const [r1,y1,r2,y2] of [[4.6,16,7.4,45],[7.4,45,8.8,54],[9.8,92,7.2,111],[7.2,111,4.7,122]]){
   bar(q,[r1,y1,0],[r2,y2,0],1.3,2.2,M.jade);bar(q,[r1+.75,y1,0],[r2+.75,y2,0],.18,.8,M.gold);
  }
  crystal(q,10.2,73,0,1.3,19,M.pale,4);
 }
 crystal(g,0,77,0,5.7,34,M.cyan,6);crystal(g,0,44,0,3,18,M.ice,6);crystal(g,0,113,0,2.7,15,M.ice,6);
 for(let i=0;i<3;i++)hoop(g,[0,77,0],12.8+i*1.7,.18,M.gold,[.4+i*.75,.3+i*.5,i*.7]);
 for(let i=0;i<8;i++){const a=i*Math.PI/4;crystal(g,13.0*Math.sin(a),77+4*Math.cos(a),13*Math.cos(a),.6,3.3,M.ice,4);}
 crystal(g,0,130,0,1.8,13,M.ice,6);cylinder(g,0,71,0,.18,.18,117,M.cyan,8);
}

function roof(g,y,size){
 box(g,0,y,0,size,.8,size,M.gold);box(g,0,y+.5,0,size-1,.5,size-1,M.roof);
 box(g,0,y+1.1,0,size-2.1,.6,size-2.1,M.roof);box(g,0,y+1.7,0,size-3.2,.6,size-3.2,M.jade);
 trimSquare(g,y-.47,size-.6,.23,M.goldLight);
 for(const x of [-1,1])for(const z of [-1,1]){
  box(g,x*(size/2-.4),y+.55,z*(size/2-.4),1.35,.48,1.35,M.goldLight);
  box(g,x*(size/2+.05),y+1.08,z*(size/2+.05),.75,.64,.75,M.goldLight);
 }
}
function outerC(g,top){
 cylinder(g,0,45,0,4.5,2.8,3.5,M.jade,8);cylinder(g,0,47.15,0,4.7,4.7,.65,M.gold,8);
 cornerPosts(g,28,46,1.2,.65,M.gold);box(g,0,36,0,1.4,17,1.4,M.shadow);crystal(g,0,25,0,1.0,7,M.cyan,4);
 const cap=top-6;
 cornerPosts(g,47,cap,2.1,.85,M.jade);cornerPosts(g,48,cap,1.62,.23,M.gold);
 for(let y=50;y<cap-4;y+=12){
  box(g,0,y+3,0,3.2,8.5,3.2,M.glass);
  for(let i=0;i<4;i++){const q=local(g,0,0,0,i*Math.PI/2);box(q,0,y+3,1.68,.22,7.5,.12,M.cyan);
   for(const x of [-.95,.95])box(q,x,y+3,1.7,.16,8.0,.12,M.gold);
   for(const yy of [y,y+5.5])box(q,0,yy,1.75,3.4,.26,.13,M.gold);
  }
  box(g,0,y-1.7,0,5.4,.6,5.4,M.gold);trimSquare(g,y-1,5.2,.25,M.jade);
 }
 roof(g,cap,8.4);box(g,0,cap+3.0,0,3.6,2.2,3.6,M.jade);roof(g,cap+4.6,6.4);
 crystal(g,0,cap+9,0,1.2,6,M.ice,4);cylinder(g,0,cap+11.6,0,.0,.7,2,M.gold,4,0);
 for(let i=0;i<4;i++){const a=i*Math.PI/2+Math.PI/4;const x=Math.sin(a)*4,z=Math.cos(a)*4;bar(g,[x,cap-.5,z],[x,cap-4,z],.14,.14,M.gold);crystal(g,x,cap-4,z,.45,1.8,M.warm,4);}
}
function centralC(g){
 cornerPosts(g,15,117,4.25,1.6,M.jade);cornerPosts(g,16,117,3.35,.34,M.gold);
 for(const y of [23,42,66,91,112]){
  ring(g,y,6.6,1.3,1.7,M.jade,8);ring(g,y+1,7,.5,.4,M.gold,8);
  for(let i=0;i<4;i++){const q=local(g,0,0,0,i*Math.PI/2);
   box(q,0,y+5,4.3,7.3,.7,1,M.gold);bar(q,[-3.5,y+5,4.3],[-1.8,y+1,4.3],.5,.5,M.jade);bar(q,[3.5,y+5,4.3],[1.8,y+1,4.3],.5,.5,M.jade);
  }
 }
 for(const [y,s] of [[48,15.5],[83,17.3],[116,19]])roof(g,y,s);
 box(g,0,120.4,0,7,4.2,7,M.jade);roof(g,123,12.6);box(g,0,126.5,0,3.7,2.6,3.7,M.glass);roof(g,128.4,7.3);crystal(g,0,133,0,1.5,6,M.ice,4);
 crystal(g,0,72,0,3.5,18,M.cyan,6);hoop(g,[0,72,0],9.0,.15,M.gold,[.35,.25,.25]);hoop(g,[0,72,0],8,.14,M.gold,[-.55,.3,1]);
 cylinder(g,0,72,0,.16,.16,99,M.cyan,8);
 for(let i=0;i<4;i++){const a=i*Math.PI/2+Math.PI/4;const x=7.7*Math.sin(a),z=7.7*Math.cos(a);bar(g,[x,116,z],[x,109,z],.15,.15,M.gold);crystal(g,x,109,z,.9,4,M.warm,4);}
}

let root;const names=['A','B','C','D'];
function build(variant,detail=false){
 if(root)scene.remove(root);root=new T.Group();scene.add(root);
 const outer=[outerA,outerB,outerC,outerA][variant],central=[centralA,centralB,centralC,centralB][variant];
 if(!detail){common(root);for(let i=0;i<8;i++){const a=i*Math.PI/4;const p=local(root,41*Math.sin(a),0,41*Math.cos(a),a);outer(p,i%2===0?105:92);}central(root);}
 else {
  const p=local(root,-24,0,0);outer(p,105);const c=local(root,14,0,0);central(c);
  ring(c,63,6.6,4.7,1.8,M.jade,48);ring(c,64.1,7.7,.3,.35,M.gold);
 }
 camera.position.set(...(detail?[100,106,280]:[116,185,280]));camera.lookAt(detail?-1:0,68,0);
 const size=detail?73:83;camera.left=-size*W/H;camera.right=size*W/H;camera.top=size;camera.bottom=-size;camera.updateProjectionMatrix();
 renderer.render(scene,camera);return {variant:names[variant],detail,meshes:renderer.info.render.calls,triangles:renderer.info.render.triangles};
}
function capture(glow=false){
 const orig=[];const bg=scene.background;
 if(glow){scene.background=new T.Color(0);root.traverse(o=>{if(o.isMesh){orig.push([o,o.material]);o.material=new T.MeshBasicMaterial({color:o.material.userData.glow||0x000000,side:T.DoubleSide});}});}
 renderer.render(scene,camera);const data=renderer.domElement.toDataURL('image/png');
 if(glow){for(const [o,m] of orig){o.material.dispose();o.material=m;}scene.background=bg;}
 return data;
}
window.build=build;window.capture=capture;window.rendererReady=true;
build(0);
