-- Seed user: test@example.com / password123 (bcrypt cost 12)
INSERT INTO users (id, name, email, password)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Test Admin', 'test@example.com',
        '$2b$12$g002nY20q/rL16v7.h0vx.tccCj1twXYM/xGRKARypU5UzlWHear6');

-- Seed project
INSERT INTO projects (id, name, description, owner_id)
VALUES ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Website Redesign', 'Q2 redesign of the company website',
        'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');

-- Seed tasks with different statuses
INSERT INTO tasks (id, title, description, status, priority, project_id, assignee_id, created_by, due_date)
VALUES
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'Design homepage mockup', 'Create wireframes and high-fidelity mockups for the new homepage', 'todo', 'high',
     'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '2026-04-20'),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 'Implement authentication', 'Set up JWT-based auth with login and registration', 'in_progress', 'medium',
     'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '2026-04-15'),
    ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', 'Set up CI/CD pipeline', 'Configure GitHub Actions for automated testing and deployment', 'done', 'low',
     'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', NULL, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', '2026-04-10');
