import { FileCode2, Folder } from 'lucide-react'
import type { ProjectFile } from '../services/reviewService'

interface ProjectTreeProps {
  files: ProjectFile[]
}

export function ProjectTree({ files }: ProjectTreeProps) {
  return (
    <section className="panel project-tree" aria-labelledby="project-files-title">
      <div className="panel-heading">
        <div><p className="eyebrow">Imported project</p><h2 id="project-files-title">Project files</h2></div>
        <button className="text-button" type="button">Manage filters</button>
      </div>
      <div className="tree-list">
        {files.map((file) => (
          <div className="tree-row" key={file.path} style={{ paddingLeft: `${16 + file.depth * 18}px` }}>
            {file.kind === 'folder' ? <Folder size={16} /> : <FileCode2 size={16} />}
            <span>{file.path}</span>
            {file.status && <small className={file.status}>{file.status}</small>}
          </div>
        ))}
      </div>
    </section>
  )
}
