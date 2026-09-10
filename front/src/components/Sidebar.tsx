import { Archive, FileText, FolderTree, Gauge, Settings2 } from 'lucide-react'

export type WorkspaceView = 'overview' | 'project' | 'analysis' | 'reports' | 'settings'

interface SidebarProps {
  activeView: WorkspaceView
  onViewChange: (view: WorkspaceView) => void
}

const navigation = [
  { id: 'overview', label: 'Overview', icon: Gauge },
  { id: 'project', label: 'Project files', icon: FolderTree },
  { id: 'analysis', label: 'Analysis', icon: Archive },
  { id: 'reports', label: 'Reports', icon: FileText },
  { id: 'settings', label: 'Settings', icon: Settings2 },
] as const

export function Sidebar({ activeView, onViewChange }: SidebarProps) {
  return (
    <aside className="sidebar">
      <div className="brand"><span>AR</span> Atlas Review</div>
      <nav aria-label="Workspace navigation">
        {navigation.map(({ id, label, icon: Icon }) => (
          <button
            className={activeView === id ? 'nav-item active' : 'nav-item'}
            key={id}
            onClick={() => onViewChange(id)}
            type="button"
          >
            <Icon size={18} />
            {label}
          </button>
        ))}
      </nav>
      <div className="sidebar-footer">
        <span className="avatar">AB</span>
        <div><strong>Alex Brown</strong><small>Evaluator</small></div>
      </div>
    </aside>
  )
}
