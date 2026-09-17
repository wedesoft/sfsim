#version 450 core

uniform sampler2D flood;
uniform int step;
uniform int size;
uniform float scale;

layout (location = 0) out vec4 point;

float shockfront(float radial_distance, float curvature_radius);

vec4 nearest(vec4 result, vec2 uv_fragment, vec2 dpos)
{
  vec4 point = texture(flood, uv_fragment + dpos);
  float current = result.z + shockfront(length(result.xy - gl_FragCoord.xy * scale), result.w);
  float candidate = point.z + shockfront(length(point.xy - gl_FragCoord.xy * scale), point.w);
  if (candidate > current)
    return point;
  else
    return result;
}

void main()
{
  float delta = float(step) / size;
  vec2 uv_fragment = gl_FragCoord.xy / size;
  vec4 result = texture(flood, uv_fragment);
  result = nearest(result, uv_fragment, vec2(-delta, -delta));
  result = nearest(result, uv_fragment, vec2(     0, -delta));
  result = nearest(result, uv_fragment, vec2(+delta, -delta));
  result = nearest(result, uv_fragment, vec2(-delta,      0));
  result = nearest(result, uv_fragment, vec2(     0,      0));
  result = nearest(result, uv_fragment, vec2(+delta,      0));
  result = nearest(result, uv_fragment, vec2(-delta, +delta));
  result = nearest(result, uv_fragment, vec2(     0, +delta));
  result = nearest(result, uv_fragment, vec2(+delta, +delta));
  point = result;
}
