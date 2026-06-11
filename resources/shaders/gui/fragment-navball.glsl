#version 130

#define PI 3.1415926535897932384626433832795

out vec4 fragColor;
uniform sampler2D navball;
uniform mat3 orientation;

void main()
{
  vec2 coords = (gl_FragCoord.xy - vec2(128, 128)) / vec2(128, 128);
  if (length(coords) <= 1.0) {
    float z = sqrt(1.0 - length(coords) * length(coords));
    vec3 p = orientation * vec3(coords.y, z, coords.x);
    float lon = atan(p.x, p.y);
    float lat = atan(p.z, length(p.yx));
    vec2 uv = vec2(lon / (2 * PI) + 0.5, lat / PI + 0.5);
    fragColor = texture(navball, uv);
  } else
    fragColor = vec4(0, 0, 0, 1);
}
