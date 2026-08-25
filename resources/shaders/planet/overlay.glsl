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
