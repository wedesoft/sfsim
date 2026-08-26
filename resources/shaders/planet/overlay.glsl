#version 450 core

uniform mat4 camera_to_overlay;
uniform float overlay_dx;
uniform float overlay_dy;
uniform sampler2D diffuse_tex;
uniform sampler2D normal_tex;

vec4 overlay_color(vec4 camera_point)
{
  vec4 overlay_point = camera_to_overlay * camera_point;
  if (overlay_point.x >= 0 && overlay_point.x <= overlay_dx && overlay_point.y >= 0 && overlay_point.y <= overlay_dy) {
    float scale = 1.0 / overlay_dx;
    vec2 uv = vec2(overlay_point.x * scale, overlay_point.y * scale);
    return vec4(texture(diffuse_tex, uv).rgb, 1.0);
  } else {
    return vec4(0.0, 0.0, 0.0, 0.0);
  };
}

vec4 overlay_normal(vec4 camera_point)
{
  vec4 overlay_point = camera_to_overlay * camera_point;
  float scale = 1.0 / overlay_dx;
  vec2 uv = vec2(overlay_point.x * scale, overlay_point.y * scale);
  vec3 dp1 = dFdx(camera_point.xyz);
  vec3 dp2 = dFdy(camera_point.xyz);
  vec2 duv1 = dFdx(uv);
  vec2 duv2 = dFdy(uv);
  vec3 T = normalize(duv2.y * dp1 - duv1.y * dp2);
  vec3 B = normalize(duv1.x * dp2 - duv2.x * dp1);
  vec3 N = normalize(cross(T, B));
  mat3 TBN = mat3(T, B, N);
  vec3 normal = 2.0 * texture(normal_tex, uv).rgb - 1.0;
  return vec4(TBN * normal, 0);
}
