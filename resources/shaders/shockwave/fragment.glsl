#version 450 core

uniform sampler2D points;
uniform sampler2D flood;
uniform mat4 camera_to_ndc;
uniform float shockwave_radius;
uniform float scale;
uniform int width;
uniform int height;
uniform float step;

out vec4 fragColor;

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float shockfront(float distance, float Rn);
float sampling_offset();

void main()
{
  vec2 uv = gl_FragCoord.xy / vec2(width, height);
  vec3 origin = (camera_to_ndc * vec4(0, 0, 0, 1)).xyz;
  vec4 point = texture(points, uv);
  vec3 direction = (camera_to_ndc * vec4(point.xyz, 0)).xyz;
  direction = normalize(direction * vec3(1, 1, 2)) / vec3(1, 1, 2);
  vec2 segment = ray_box(vec3(-1, -1, 0), vec3(1, 1, 1), origin, direction);
  if (point.w > 0.0) {
    vec4 surface = camera_to_ndc * point;
    float dist = length(surface.xyz - origin) / length(direction);
    if (segment.x + segment.y > dist) {
      segment.y = dist - segment.x;
    };
  };
  float emission = 0.0;
  float x = segment.x + step * sampling_offset();
  while (x < segment.x + segment.y) {
    vec3 p = origin + x * direction;
    vec4 point = texture(flood, (p.xy + 1.0) / 2.0);
    float l = length(point.xy - (p.xy + 1.0) * shockwave_radius);
    float depth = point.z + shockfront(l, point.w);
    if (p.z * shockwave_radius <= depth) {
      emission += 4.0 * step * exp(1.0 * (p.z * shockwave_radius - depth)) * (1.0 - smoothstep(0.0, 0.25 * shockwave_radius, l));
    };
    x += step;
  };
  fragColor = vec4(vec3(emission, emission, 0.0), 0.0);
}
