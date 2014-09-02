module.exports = function(grunt) {
  grunt.initConfig({
    less: {
      development: {
        options: {
          paths: ["deps"],
          compress: true,
          yuicompress: true,
          optimization: 2
        },
        files: {
          "resources/public/css/style.css": "src/less/style.less"
        }
      }
    },
    watch: {
      styles: {
        files: ['src/less/**/*.less'], // which files to watch
        tasks: ['less'],
        options: {
          nospawn: true
        }
      }
    }
  });

  grunt.loadNpmTasks('grunt-contrib-less');
  grunt.loadNpmTasks('grunt-contrib-watch');

  grunt.registerTask('default', ['watch']);
};

