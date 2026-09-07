#version 450 core
uniform mat4 projection;
uniform mat4 object_to_camera;

in vec3 vertex;
<% (when bump %>
in vec3 tangent;
in vec3 bitangent;
<% ) %>
in vec3 normal;
<% (when (or textured bump) %>
in vec2 texcoord;
<% ) %>

out VS_OUT
{
  vec4 camera_point;
<% (when (and full (not bump)) %>
  vec4 normal;
<% ) %>
<% (when (and full bump) %>
  mat3 surface;
<% ) %>
<% (when (and full (or textured bump)) %>
  vec2 texcoord;
<% ) %>
} vs_out;

void main()
{
  vec4 camera_point = object_to_camera * vec4(vertex, 1);
  vs_out.camera_point = camera_point;
<% (when (and full (not bump)) %>
  vs_out.normal = object_to_camera * vec4(normal, 0);
<% ) %>
<% (when (and full bump) %>
  vs_out.surface = mat3(object_to_camera) * mat3(tangent, bitangent, normal);
<% ) %>
<% (when (and full (or textured bump)) %>
  vs_out.texcoord = texcoord;
<% ) %>
  gl_Position = projection * camera_point;
}
