#version 130

#define PI 3.1415926535897932384626433832795

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

// https://spacedock.info/mod/3738/KSP2%20Pre-Alpha%20Style%20NavBall
uniform sampler2D navball;

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 coords = (fragCoord - iResolution.xy / 2.0) / (iResolution.x / 2.0);
  if (length(coords) <= 1.0) {
    float x = sqrt(1.0 - length(coords) * length(coords));
    vec3 p = vec3(x, coords.x, -coords.y);
    float alpha = (iMouse.x / iResolution.x - 0.5) * PI;
    mat3 rotz = mat3(cos(alpha), -sin(alpha), 0.0, sin(alpha), cos(alpha), 0.0, 0.0, 0.0, 1.0);
    float beta = (iMouse.y / iResolution.y - 0.5) * PI;
    mat3 roty = mat3(cos(beta), 0.0, sin(beta), 0.0, 1.0, 0.0, -sin(beta), 0.0, cos(beta));
    p = rotz * roty * p;
    float lon = atan(p.y, p.x);
    float lat = atan(p.z, length(p.xy));
    vec2 uv = vec2(lon / (2 * PI) + 0.5, lat / PI + 0.5);
    fragColor = texture(navball, uv);
  } else {
    fragColor = vec4(0, 0, 0, 1);
  };
}
