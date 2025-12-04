#version 450 core

layout(triangles) in;

in TES_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
<% (doseq [i (range num-scene-shadows)] %>
  vec4 object_shadow_pos_<%= (inc ^long i) %>;
<% ) %>
} geo_in[3];

layout(triangle_strip, max_vertices = 3) out;

out GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
<% (doseq [i (range num-scene-shadows)] %>
  vec4 object_shadow_pos_<%= (inc ^long i) %>;
<% ) %>
} geo_out;

// Shader to output triangles with texture and surface point cloud coordinates.
void main(void)
{
  gl_Position = gl_in[0].gl_Position;
  geo_out.colorcoord = geo_in[0].colorcoord;
  geo_out.point = geo_in[0].point;
  geo_out.object_point = geo_in[0].object_point;
  geo_out.camera_point = geo_in[0].camera_point;
<% (doseq [i (range num-scene-shadows)] %>
  geo_out.object_shadow_pos_<%= (inc ^long i) %> = geo_in[0].object_shadow_pos_<%= (inc ^long i) %> ;
<% ) %>
  EmitVertex();
  gl_Position = gl_in[1].gl_Position;
  geo_out.colorcoord = geo_in[1].colorcoord;
  geo_out.point = geo_in[1].point;
  geo_out.object_point = geo_in[1].object_point;
  geo_out.camera_point = geo_in[1].camera_point;
<% (doseq [i (range num-scene-shadows)] %>
  geo_out.object_shadow_pos_<%= (inc ^long i) %> = geo_in[1].object_shadow_pos_<%= (inc ^long i) %> ;
<% ) %>
  EmitVertex();
  gl_Position = gl_in[2].gl_Position;
  geo_out.colorcoord = geo_in[2].colorcoord;
  geo_out.point = geo_in[2].point;
  geo_out.object_point = geo_in[2].object_point;
  geo_out.camera_point = geo_in[2].camera_point;
<% (doseq [i (range num-scene-shadows)] %>
  geo_out.object_shadow_pos_<%= (inc ^long i) %> = geo_in[2].object_shadow_pos_<%= (inc ^long i) %> ;
<% ) %>
  EmitVertex();
  EndPrimitive();
}
