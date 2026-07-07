#version 450 core
uniform mat4 projection;
uniform mat4 object_to_camera;

in vec3 vertex;
<% (if bump %>
in vec3 tangent;
in vec3 bitangent;
<% ) %>
in vec3 normal;
<% (if (or textured bump) %>
in vec2 texcoord;
<% ) %>

out VS_OUT
{
  vec4 camera_point;
<% (if full %>
  vec4 normal;
<% ) %>
} vs_out;

void main()
{
  vec4 camera_point = object_to_camera * vec4(vertex, 1);
  vs_out.camera_point = camera_point;
<% (if full %>
  vs_out.normal = object_to_camera * vec4(normal, 0);
<% ) %>
  gl_Position = projection * camera_point;
}
