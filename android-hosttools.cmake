# Included via CMAKE_PROJECT_INCLUDE for the Android cross build.
# Build-time generator executables cannot run on the Windows host when built
# for Android, so each is replaced by an IMPORTED target that points to the
# natively built host copy.
set(HOST_TOOLS_DIR "D:/android-dev/host-build/runtime_output_directory"
    CACHE PATH "Directory with host-built generator tools")
set(HOST_TOOL_NAMES protoc comp_err gen_keyword_list gen_lex_hash gen_lex_token comp_sql xprotocol_plugin uca9dump)

if(NOT COMMAND _add_executable)
  function(add_executable name)
    if(name IN_LIST HOST_TOOL_NAMES)
      _add_executable(${name} IMPORTED GLOBAL)
      set_target_properties(${name} PROPERTIES
        IMPORTED_LOCATION "${HOST_TOOLS_DIR}/${name}.exe")
      return()
    endif()
    _add_executable(${name} ${ARGN})
  endfunction()
endif()

# Build-configuration commands on an imported host tool are meaningless; ignore them.
foreach(_cmd target_link_libraries target_compile_features target_compile_definitions
        target_compile_options target_include_directories target_link_options
        target_link_directories target_sources)
  if(NOT COMMAND _${_cmd})
    cmake_language(EVAL CODE "
      function(${_cmd} name)
        if(name IN_LIST HOST_TOOL_NAMES)
          return()
        endif()
        _${_cmd}(\${name} \${ARGN})
      endfunction()")
  endif()
endforeach()

# Allow find_path/find_library to see host-side dirs (Boost, OpenSSL, bison).
foreach(m INCLUDE LIBRARY PROGRAM PACKAGE)
  set(CMAKE_FIND_ROOT_PATH_MODE_${m} BOTH)
endforeach()




