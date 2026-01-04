#version 450 core

uniform sampler2D camera_point;
uniform int overlay_width;
uniform int overlay_height;

vec4 geometry_point()
{
  vec2 uv = vec2(gl_FragCoord.x / overlay_width, gl_FragCoord.y / overlay_height);
  return texture(camera_point, uv);
}
