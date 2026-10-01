#version 450 core

uniform sampler2D points;
uniform mat4 camera_to_ndc;
uniform float scale;
uniform int width;
uniform int height;
uniform float shockwave_step;

out vec4 fragColor;

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float sampling_offset();
vec4 shockwave_transfer(vec3 p, float shockwave_step, vec4 shockwave_scatter);

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
  vec4 shockwave_scatter = vec4(0, 0, 0, 0);
  float x = segment.x + shockwave_step * sampling_offset();
  while (x < segment.x + segment.y) {
    vec3 point = origin + x * direction;
    shockwave_scatter = shockwave_transfer(point, shockwave_step, shockwave_scatter);
    x += shockwave_step;
  };
  fragColor = shockwave_scatter;
}
