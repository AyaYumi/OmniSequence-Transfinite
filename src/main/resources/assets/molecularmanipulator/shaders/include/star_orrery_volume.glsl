// Shared by the reduced-resolution integrator and the full-resolution edge fallback.
uniform sampler2D SceneDepth;
uniform mat4 InverseProjection;
uniform mat4 ViewToNebula;
uniform vec3 RayOrigin;
uniform float FieldRadius;
uniform float NebulaTime;
uniform float FlowTime;
uniform float Detail;
uniform float Dynamics;
uniform mat4 NebulaToCamera;
uniform float VolumeFogStart;
uniform float VolumeFogEnd;
uniform int VolumeFogShape;

const float TAU = 6.28318530718;

vec3 viewPoint(vec2 uv, float depth) {
    vec4 p = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float surfaceDistance(vec2 uv) {
    float depth = texture(SceneDepth, uv).r;
    return depth >= 0.999999 ? 1e6 : length(viewPoint(uv, depth) - RayOrigin);
}

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 cell = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(cell), hash(cell+vec3(1,0,0)), f.x),
                   mix(hash(cell+vec3(0,1,0)), hash(cell+vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(cell+vec3(0,0,1)), hash(cell+vec3(1,0,1)), f.x),
                   mix(hash(cell+vec3(0,1,1)), hash(cell+vec3(1,1,1)), f.x), f.y), f.z);
}

float clouds(vec3 p) {
    return noise(p)*0.57 + noise(p*2.07+13.7)*0.29 + noise(p*4.19-7.1)*0.14;
}

float stellarPoints(vec2 p) {
    vec2 grid = p*63.0;
    vec2 cell = floor(grid);
    vec2 center = vec2(hash(vec3(cell,4.1)),hash(vec3(cell,9.7)))*0.60+0.20;
    float d = length(fract(grid)-center);
    return (1.0-smoothstep(0.025,0.15,d))*step(0.72,hash(vec3(cell,17.3)));
}

mat2 rotate(float angle) {
    float s = sin(angle), c = cos(angle);
    return mat2(c, -s, s, c);
}

vec3 galaxySpace(vec3 p) {
    p.yz = rotate(0.96)*p.yz;
    p.xz = rotate(-NebulaTime*TAU/80.0)*p.xz;
    return p;
}

vec4 medium(vec3 point) {
    vec3 p = galaxySpace(point / FieldRadius);
    float radius = max(length(p.xz),0.0001);
    float azimuth = atan(p.z,p.x);
    float phase = fract(NebulaTime/4.0+floor(p.y*12.0)*0.271);
    float glitch = smoothstep(0.78,0.82,phase)*(1.0-smoothstep(0.84,0.87,phase))*Dynamics;
    p.x += glitch*0.018*sin(NebulaTime*63.0+p.y*40.0);

    // Evaluate cheap density envelopes BEFORE the five 3D noises. Most of the enclosing
    // sphere lies outside the thin disk; keep the spherical bulge/core and inflow too.
    float height2 = p.y*p.y;
    float diskHeight = 0.027+0.052*exp(-radius*2.6);
    float diffuseHeight = 0.048+0.058*exp(-radius*2.6);
    float vertical = exp(-height2/(diskHeight*diskHeight));
    float diffuseVertical = exp(-height2/(diffuseHeight*diffuseHeight));
    float edge = 1.0-smoothstep(0.73,0.99,radius);
    float radius2 = dot(p,p);
    float bulge = exp(-radius2*48.0)*2.1;
    float feedEnvelope = exp(-height2*160.0)*smoothstep(0.23,0.52,radius)
            *(1.0-smoothstep(0.82,0.97,radius))*Dynamics;
    if (max(vertical,diffuseVertical)*edge+bulge+feedEnvelope < 0.000001) return vec4(0.0);

    vec3 warped = p;
    warped.xz = rotate(3.8*log(radius+0.06)+FlowTime*0.10)*warped.xz;
    float turbulence = clouds(warped*19.0+vec3(0,FlowTime*0.065,0));
    float grain = noise(vec3(warped.x*62.0,FlowTime*0.012,warped.z*62.0)+vec3(7.3,0,12.1));
    float haze = noise(warped*4.6+vec3(3.1,FlowTime*0.018,9.4));
    float spiral = azimuth+5.1*log(radius+0.055)+(turbulence-0.5)*0.28+(haze-0.5)*0.50;
    float mainArms = pow(0.5+0.5*cos(3.0*spiral+turbulence*1.1),3.0);
    float branches = pow(0.5+0.5*cos(7.0*spiral-radius*9.0+grain*1.4),5.0);
    float filaments = pow(0.5+0.5*cos(13.0*spiral+radius*29.0+turbulence*2.3),9.0);
    float dustLanes = pow(0.5+0.5*cos(7.0*spiral+0.85+radius*8.0+grain*2.0),9.0);
    float clumps = smoothstep(0.36,0.70,turbulence);
    float stars = stellarPoints(p.xz)*(0.30+mainArms*0.75+branches*0.45);
    float diffuse = (0.10+haze*0.24)*diffuseVertical*edge*(0.65+grain*0.35);
    float cloud = (0.035+mainArms*1.10+branches*0.48+filaments*0.18)
            *vertical*edge*(0.24+clumps*1.35)*(0.52+grain*0.60);
    float dust = dustLanes*vertical*edge*(0.35+grain)*0.70
            *(0.24+mainArms*0.55+branches*0.28);
    float core = exp(-radius2*290.0);
    float inflow = pow(0.5+0.5*cos(3.0*spiral+radius*31.0+FlowTime*TAU/4.0),14.0);
    float feed = inflow*feedEnvelope*(0.18+clumps);
    float lightDensity = diffuse+cloud+bulge+feed*0.42+stars*vertical*edge*0.72;
    float density = (lightDensity+dust)*(1.0-glitch*0.30);
    float tint = 0.5+0.5*sin(spiral+radius*13.0+turbulence*4.0);
    vec3 blue = vec3(0.14,0.36,0.78), violet = vec3(0.41,0.29,0.74);
    vec3 color = mix(blue,violet,tint)*(0.50+clumps*0.78);
    color = mix(color,vec3(0.78,0.43,0.80),branches*0.30);
    color += feed*vec3(0.24,0.56,1.0)+stars*vec3(3.0,3.2,3.7);
    color = mix(color,vec3(0.73,0.84,1.0),clamp(bulge*0.43,0.0,0.88));
    color = mix(color,vec3(1.0,0.76,0.54),clamp(exp(-radius2*18.0)*0.88,0.0,0.78));
    color += core*vec3(2.8,2.65,2.35);
    color *= lightDensity/max(lightDensity+dust,0.0001);
    return vec4(color,density);
}

// Return radiance and TRANSMITTANCE, never a downsampled scene color. Identity is
// written explicitly so bounded target reuse cannot expose another machine's pixels.
vec4 integrateNebula(vec2 uv) {
    vec3 viewRay = normalize(viewPoint(uv,0.5)-RayOrigin);
    vec3 origin = (ViewToNebula*vec4(RayOrigin,1.0)).xyz;
    vec3 ray = normalize((ViewToNebula*vec4(viewRay,0.0)).xyz);
    float b = dot(origin,ray);
    float discriminant = b*b-dot(origin,origin)+FieldRadius*FieldRadius;
    if (discriminant <= 0.0) return vec4(0,0,0,1);
    float near = max(0.0,-b-sqrt(discriminant));
    float far = min(-b+sqrt(discriminant),surfaceDistance(uv));
    if (far<=near) return vec4(0,0,0,1);

    int steps = Detail>0.5 ? 64 : 36;
    float stride = (far-near)/float(steps);
    vec3 radiance = vec3(0.0);
    float transmittance = 1.0;
    for (int i=0;i<64;i++) {
        if (i>=steps || transmittance<0.015) break;
        vec3 point = origin+ray*(near+(float(i)+0.5)*stride);
        float visibility = linear_fog_fade(fog_distance((NebulaToCamera*vec4(point,1.0)).xyz,
                VolumeFogShape),VolumeFogStart,VolumeFogEnd);
        if (visibility <= 0.0) continue;
        vec4 field = medium(point);
        float opacity = (1.0-exp(-field.a*stride*0.84))*visibility;
        radiance += transmittance*opacity*field.rgb;
        transmittance *= 1.0-opacity;
    }
    if (transmittance>0.999) return vec4(0,0,0,1);
    return vec4(min(radiance,vec3(1.3)),transmittance);
}
